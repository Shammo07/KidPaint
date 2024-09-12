import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.border.LineBorder;

enum PaintMode {
	Pixel, Area,
}

public class UI extends JFrame {

	String name;
	DatagramSocket udpSocket = new DatagramSocket(43215);
	Socket socket;
	DataInputStream in;
	DataOutputStream out;
	ArrayList<Integer> studios = new ArrayList<>();

	private JTextField msgField;
	private JTextArea chatArea;
	private JPanel pnlColorPicker;
	private JPanel paintPanel;
	private JToggleButton tglPen;
	private JToggleButton tglBucket;
	private JToggleButton tglEraser;
	private JButton clear;
	private JButton save;
	private JButton load;
	private int penSize = 1;

	private static UI instance;
	private int selectedColor = -543230; // golden

	int[][] data = new int[50][50]; // pixel color data array
	int blockSize = 16;
	PaintMode paintMode = PaintMode.Pixel;

	/**
	 * get the instance of UI. Singleton design pattern.
	 *
	 * @return
	 */
	public static UI getInstance() throws IOException {
		if (instance == null)
			instance = new UI();

		return instance;
	}

	private void receive(DataInputStream in) throws IOException {
		try {
			while (true) {
				int type = in.readInt();

				switch (type) {
				case 0:
					// receive text
					receiveTextMessage(in);
					break;
				case 1:
					// receive pixel message
					receivePixelMessage(in);
					break;
				case 2:
					// receive bucket message
					receiveBucketMessage(in);
					break;
				case 3:
					receiveStudioMessage(in);
				default:
				}
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	private void receiveStudioMessage(DataInputStream in) throws IOException {
		int total = in.readInt();
		for (int i = 0; i < total; i++) {
			int studio = in.readInt();
			studios.add(studio);
		}
	}

	private void receiveTextMessage(DataInputStream in) throws IOException {
		byte[] buffer = new byte[1024];

		int len = in.readInt();
		in.read(buffer, 0, len);

		String msg = new String(buffer, 0, len);
		System.out.println(msg);

		SwingUtilities.invokeLater(() -> {
			chatArea.append(msg + "\n");
		});
	}

	private void receivePixelMessage(DataInputStream in) throws IOException {
		int color = in.readInt();
		int x = in.readInt();
		int y = in.readInt();
		paintPixel(color, x, y);
		// TODO: Update the screen
	}

	private void receiveBucketMessage(DataInputStream in) throws IOException {
		int size = in.readInt();
		int color = in.readInt();

		for (int i = 0; i < size; i++) {
			int x = in.readInt();
			int y = in.readInt();
			paintPixel(color, x, y);
		}
	}

	private void sendUDPrequest(String name) throws IOException {
		byte[] req = name.getBytes();

		InetAddress dest = InetAddress.getByName("255.255.255.255");
		int port = 55555;
		DatagramPacket packet = new DatagramPacket(req, req.length, dest, port);
		udpSocket.send(packet);

		byte[] buffer = new byte[1024];
		DatagramPacket p = new DatagramPacket(buffer, buffer.length);
		udpSocket.receive(p);
		String serverInfo = new String(p.getData(), 0, p.getLength());
		String[] addresses = serverInfo.split(",");
		String serverIP = addresses[0];
		int serverPort = Integer.parseInt(addresses[1]);
		udpSocket.close();

		socket = new Socket(serverIP, serverPort);
		in = new DataInputStream(socket.getInputStream());
		out = new DataOutputStream(socket.getOutputStream());

		Thread t = new Thread(() -> {
			try {
				receive(in);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		});

		t.start();
	}

	/**
	 * private constructor. To create an instance of UI, call UI.getInstance()
	 * instead.
	 */
	private UI() throws IOException {
		JPanel basePanel = new JPanel();
		JPanel msgPanel = new JPanel();

		JPanel usernamePanel = new JPanel();

		JLabel label = new JLabel("Input your name");
		usernamePanel.add(label);
		usernamePanel.setBackground(new Color(15, 14, 23));

		label.setForeground(new Color(255, 255, 254));
		JTextField username = new JTextField();
		usernamePanel.add(username);
		username.setPreferredSize(new Dimension(200, 30));
		JButton btn = new JButton("Submit");
		btn.setBackground(new Color(255, 137, 6));

		usernamePanel.add(btn);

		btn.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				name = username.getText() + ": ";
				System.out.println(name);
				getContentPane().remove(usernamePanel);
				try {
					sendUDPrequest(name);
				} catch (IOException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}

				JPanel studioPanel = new JPanel();
				studioPanel.setLayout(new BorderLayout());

				JPanel createPanel = new JPanel(new FlowLayout());

				JButton create = new JButton("Create New Studio");
				createPanel.add(create);

				create.addActionListener(new ActionListener() {
					public void actionPerformed(ActionEvent e1) {
						try {
							out.writeInt(studios.size() + 1);
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}

						String w = JOptionPane.showInputDialog("Enter width (at least 40): ");
						String h = JOptionPane.showInputDialog("Enter height (at least 40): ");

						try {
							int width = Integer.parseInt(w);
							int height = Integer.parseInt(h);

							if (width >= 40 && height >= 40) {
								int[][] newData = new int[width][height];
								data = newData;

								getContentPane().remove(studioPanel);
								getContentPane().add(basePanel, BorderLayout.CENTER);
								getContentPane().add(msgPanel, BorderLayout.EAST);
								validate();
								repaint();
							} else {
								JOptionPane.showMessageDialog(null, "Number cannot be less than 40", "Error",
										JOptionPane.ERROR_MESSAGE);
							}

						} catch (NumberFormatException ex) {
							JOptionPane.showMessageDialog(null, "Please enter a number", "Error",
									JOptionPane.ERROR_MESSAGE);
						}
					}
				});
				studioPanel.add(createPanel, BorderLayout.NORTH);

				JPanel optionsPanel = new JPanel(new FlowLayout());

				JPanel selectionPanel = new JPanel(new FlowLayout());

				if (studios.size() > 0) {
					JLabel studiosAvailable = new JLabel("Available Studios: ");
					optionsPanel.add(studiosAvailable);

					for (int i = 1; i <= studios.size(); i++) {
						JLabel studioNumber = new JLabel("Studio " + i + " |");
						optionsPanel.add(studioNumber);
					}

					JLabel studioSelectInstruction = new JLabel("Input the studio number: ");
					selectionPanel.add(studioSelectInstruction);

					JTextField txt = new JTextField();
					txt.setPreferredSize(new Dimension(100, 100));
					selectionPanel.add(txt);

					JButton choice = new JButton("Choose");
					selectionPanel.add(choice);

					choice.addActionListener(new ActionListener() {
						public void actionPerformed(ActionEvent e2) {
							String textInput = txt.getText();
							try {
								int studioChosen = Integer.parseInt(textInput);
								if (studioChosen < 1 || studioChosen > studios.size()) {
									JOptionPane.showMessageDialog(null, "A studio with this number does not exist",
											"Error", JOptionPane.ERROR_MESSAGE);
								} else {
									out.writeInt(studioChosen);
									getContentPane().remove(studioPanel);
									getContentPane().add(basePanel, BorderLayout.CENTER);
									getContentPane().add(msgPanel, BorderLayout.EAST);
									validate();
									repaint();
								}
							} catch (NumberFormatException e) {
								JOptionPane.showMessageDialog(null, "Please enter a number", "Error",
										JOptionPane.ERROR_MESSAGE);
							} catch (IOException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						}
					});
				}

				studioPanel.add(optionsPanel, BorderLayout.CENTER);

				studioPanel.add(selectionPanel, BorderLayout.SOUTH);

				getContentPane().add(studioPanel);
				validate();
				repaint();
			}
		});

		getContentPane().add(usernamePanel);

		setTitle("KidPaint");

		// JPanel basePanel = new JPanel();
		// getContentPane().add(basePanel, BorderLayout.CENTER);
		basePanel.setLayout(new BorderLayout(0, 0));

		paintPanel = new JPanel() {
			// refresh the paint panel
			@Override
			public void paint(Graphics g) {
				super.paint(g);

				Graphics2D g2 = (Graphics2D) g; // Graphics2D provides the setRenderingHints method

				// enable anti-aliasing
				RenderingHints rh = new RenderingHints(RenderingHints.KEY_ANTIALIASING,
						RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setRenderingHints(rh);

				// clear the paint panel using black
				g2.setColor(Color.black);
				g2.fillRect(0, 0, this.getWidth(), this.getHeight());

				// draw and fill circles with the specific colors stored in the data array
				for (int x = 0; x < data.length; x++) {
					for (int y = 0; y < data[0].length; y++) {
						g2.setColor(new Color(data[x][y]));
						g2.fillArc(blockSize * x, blockSize * y, blockSize, blockSize, 0, 360);
						g2.setColor(Color.darkGray);
						g2.drawArc(blockSize * x, blockSize * y, blockSize, blockSize, 0, 360);
					}
				}
			}
		};

		paintPanel.addMouseListener(new MouseListener() {
			@Override
			public void mouseClicked(MouseEvent e) {
			}

			@Override
			public void mouseEntered(MouseEvent e) {
			}

			@Override
			public void mouseExited(MouseEvent e) {
			}

			@Override
			public void mousePressed(MouseEvent e) {
			}

			// handle the mouse-up event of the paint panel
			@Override
			public void mouseReleased(MouseEvent e) {
				if (paintMode == PaintMode.Area && e.getX() >= 0 && e.getY() >= 0)
					paintArea(e.getX() / blockSize, e.getY() / blockSize);
			}
		});

		paintPanel.addMouseMotionListener(new MouseMotionListener() {
			@Override
			public void mouseDragged(MouseEvent e) {
				if (paintMode == PaintMode.Pixel && e.getX() >= 0 && e.getY() >= 0 && tglPen.isSelected()) {
					// paintPixel(e.getX() / blockSize, e.getY() / blockSize);
					try {
						// send data to the server instead of updating the screen
						if (penSize == 1 || penSize == 2 || penSize == 3) {
							out.writeInt(1);
							out.writeInt(selectedColor);
							out.writeInt(e.getX() / blockSize);
							out.writeInt(e.getY() / blockSize);
							out.flush();
						}
						if (penSize == 2 || penSize == 3) {
							out.writeInt(1);
							out.writeInt(selectedColor);
							// out.writeInt(e.getX() / blockSize);
							out.writeInt((e.getX() / (blockSize)) + 1);
							out.writeInt((e.getY() / (blockSize)) + 1);
							// out.writeInt(e.getX() / (blockSize / 2));
							// out.writeInt(e.getY() / (blockSize / 2));
							out.flush();
						}
						if (penSize == 3) {
							out.writeInt(1);
							out.writeInt(selectedColor);
							out.writeInt((e.getX() / (blockSize)) - 1);
							out.writeInt((e.getY() / (blockSize)) - 1);
							out.flush();
						}
					} catch (IOException ex) {
						ex.printStackTrace(); // for debugging, remove it in production stage
					}
				} else {
					if (paintMode == PaintMode.Pixel && e.getX() >= 0 && e.getY() >= 0 && tglEraser.isSelected())
						try { // Eraser Mode (Pixels == BLACK)
							// send data to the server instead of updating the screen
							out.writeInt(1);
							out.writeInt(0);
							out.writeInt(e.getX() / blockSize);
							out.writeInt(e.getY() / blockSize);
							out.flush();
						} catch (IOException ex) {
							ex.printStackTrace(); // for debugging, remove it in production stage
						}
				}
			}

			@Override
			public void mouseMoved(MouseEvent e) {
			}
		});

		paintPanel.setPreferredSize(new Dimension(data.length * blockSize, data[0].length * blockSize));

		JScrollPane scrollPaneLeft = new JScrollPane(paintPanel, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
				JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

		basePanel.add(scrollPaneLeft, BorderLayout.CENTER);

		JPanel toolPanel = new JPanel();
		basePanel.add(toolPanel, BorderLayout.NORTH);
		toolPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 1, 1));

		pnlColorPicker = new JPanel();
		pnlColorPicker.setPreferredSize(new Dimension(24, 24));
		pnlColorPicker.setBackground(new Color(selectedColor));
		pnlColorPicker.setBorder(new LineBorder(new Color(0, 0, 0)));

		// show the color picker
		pnlColorPicker.addMouseListener(new MouseListener() {
			@Override
			public void mouseClicked(MouseEvent e) {
			}

			@Override
			public void mouseEntered(MouseEvent e) {
			}

			@Override
			public void mouseExited(MouseEvent e) {
			}

			@Override
			public void mousePressed(MouseEvent e) {
			}

			@Override
			public void mouseReleased(MouseEvent e) {
				ColorPicker picker = ColorPicker.getInstance(UI.instance);
				Point location = pnlColorPicker.getLocationOnScreen();
				location.y += pnlColorPicker.getHeight();
				picker.setLocation(location);
				picker.setVisible(true);
			}
		});

		toolPanel.add(pnlColorPicker);

		tglPen = new JToggleButton("Pen " + penSize);
		tglPen.setSelected(true);
		toolPanel.add(tglPen);

		// Changing pen button
		tglPen.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				penSize++;
				if (penSize > 3) { // Reset penSize to 1 if it exceeds 3
					penSize = 1;
				}
				tglPen.setText("Pen " + penSize);
			}
		});

		tglBucket = new JToggleButton("Bucket");
		toolPanel.add(tglBucket);

		tglEraser = new JToggleButton("Eraser");
		toolPanel.add(tglEraser);

		clear = new JButton("Clear");
		toolPanel.add(clear);

		save = new JButton("Save");
		toolPanel.add(save);

		load = new JButton("Load");
		toolPanel.add(load);

		clear.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				LinkedList<Point> pixels = new LinkedList<>();
				for (int i = 0; i < data.length; i++) {
					for (int j = 0; j < data[i].length; j++) {
						pixels.add(new Point(i, j));
					}
				}

				try {
					out.writeInt(3);
					out.writeInt(pixels.size());
					out.writeInt(Color.black.getRGB());
					for (Point p : pixels) {
						out.writeInt(p.x);
						out.writeInt(p.y);
					}
					out.flush();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		});

		// change the paint mode to pixel FOR ERASER MODE
		tglEraser.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				tglPen.setSelected(false);
				tglBucket.setSelected(false);
				tglEraser.setSelected(true);
				paintMode = PaintMode.Pixel;
			}
		});

		// change the paint mode to PIXEL mode FOR PEN MODE
		tglPen.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				tglPen.setSelected(true);
				tglBucket.setSelected(false);
				tglEraser.setSelected(false);
				paintMode = PaintMode.Pixel;
			}
		});

		// change the paint mode to AREA mode FOR BUCKET MODE
		tglBucket.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				tglPen.setSelected(false);
				tglBucket.setSelected(true);
				tglEraser.setSelected(false);
				paintMode = PaintMode.Area;
			}
		});

		save.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				JFileChooser fileChooser = new JFileChooser();
				int fileChosen = fileChooser.showSaveDialog(UI.this);
				if (fileChosen == JFileChooser.APPROVE_OPTION) {
					File selectedFile = fileChooser.getSelectedFile();

					try (PrintWriter pw = new PrintWriter(selectedFile)) {
						pw.write(String.valueOf(data.length) + " ");
						pw.write(String.valueOf(data[0].length) + " ");
						pw.write(String.valueOf(blockSize));
						pw.println();

						for (int col = 0; col < data.length; col++) {
							for (int row = 0; row < data[col].length; row++) {
								pw.write(String.valueOf(data[col][row]) + " ");
							}
							pw.println();
						}

						pw.flush();

						JOptionPane.showMessageDialog(UI.this, "File saved successfully!");
					} catch (IOException ex) {
						JOptionPane.showMessageDialog(UI.this, "Error saving file: " + ex.getMessage());
					}
				} else {
					System.out.println("File not saved");
				}
			}
		});

		load.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				JFileChooser fileChooser = new JFileChooser();
				int fileChosen = fileChooser.showOpenDialog(UI.this);
				if (fileChosen == JFileChooser.APPROVE_OPTION) {
					File selectedFile = fileChooser.getSelectedFile();
					try (Scanner scanner = new Scanner(selectedFile)) {
						int r = Integer.valueOf(scanner.next());
						int c = Integer.valueOf(scanner.next());
						int[][] newData = new int[r][c];
						int newBlockSize = Integer.valueOf(scanner.next());
						scanner.nextLine();

						for (int i = 0; scanner.hasNextLine(); i++) {
							String[] row = scanner.nextLine().split(" ");
							for (int j = 0; j < row.length; j++)
								newData[j][i] = Integer.valueOf(row[j]);
						}

						data = newData;
						blockSize = newBlockSize;
						paintPanel.repaint();
					} catch (FileNotFoundException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
				}
			}
		});

		// JPanel msgPanel = new JPanel();
		//
		// getContentPane().add(msgPanel, BorderLayout.EAST);
		//
		msgPanel.setLayout(new BorderLayout(0, 0));

		msgField = new JTextField(); // text field for inputting message

		msgPanel.add(msgField, BorderLayout.SOUTH);

		// handle key-input event of the message field
		msgField.addKeyListener(new KeyListener() {
			@Override
			public void keyTyped(KeyEvent e) {
			}

			@Override
			public void keyPressed(KeyEvent e) {
			}

			@Override
			public void keyReleased(KeyEvent e) {
				if (e.getKeyCode() == 10) { // if the user press ENTER
					onTextInputted(msgField.getText());
					msgField.setText("");
				}
			}
		});

		chatArea = new JTextArea(); // the read only text area for showing messages
		chatArea.setEditable(false);
		chatArea.setLineWrap(true);

		JScrollPane scrollPaneRight = new JScrollPane(chatArea, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
				JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		scrollPaneRight.setPreferredSize(new Dimension(300, this.getHeight()));
		msgPanel.add(scrollPaneRight, BorderLayout.CENTER);

		this.setSize(new Dimension(800, 600));
		this.setDefaultCloseOperation(EXIT_ON_CLOSE);
	}

	/**
	 * it will be invoked if the user selected the specific color through the color
	 * picker
	 *
	 * @param colorValue - the selected color
	 */
	public void selectColor(int colorValue) {
		SwingUtilities.invokeLater(() -> {
			selectedColor = colorValue;
			pnlColorPicker.setBackground(new Color(colorValue));
		});
	}

	/**
	 * it will be invoked if the user inputted text in the message field
	 *
	 * @param text - user inputted text
	 */
	private void onTextInputted(String text) {
		// chatArea.setText(chatArea.getText() + text + "\n");
		try {
			out.writeInt(0); // 0 means this is a chat message

			out.writeInt(name.length());
			out.write(name.getBytes(), 0, name.length());
			out.writeInt(text.length());
			out.write(text.getBytes());
			out.flush();
		} catch (IOException e) {
		}
	}

	/**
	 * change the color of a specific pixel
	 *
	 * @param col, row - the position of the selected pixel
	 */
	public void paintPixel(int col, int row) {
		if (col >= data.length || row >= data[0].length)
			return;

		data[col][row] = selectedColor;
		paintPanel.repaint(col * blockSize, row * blockSize, blockSize, blockSize);
	}

	public void paintPixel(int color, int col, int row) {
		if (col >= data.length || row >= data[0].length)
			return;

		data[col][row] = color;
		paintPanel.repaint(col * blockSize, row * blockSize, blockSize, blockSize);
	}

	/**
	 * change the color of a specific area
	 *
	 * @param col, row - the position of the selected pixel
	 * @return a list of modified pixels
	 */
	public List paintArea(int col, int row) {
		LinkedList<Point> filledPixels = new LinkedList<Point>();

		if (col >= data.length || row >= data[0].length)
			return filledPixels;

		int oriColor = data[col][row];
		LinkedList<Point> buffer = new LinkedList<Point>();

		if (oriColor != selectedColor) {
			buffer.add(new Point(col, row));

			while (!buffer.isEmpty()) {
				Point p = buffer.removeFirst();
				int x = p.x;
				int y = p.y;

				if (data[x][y] != oriColor)
					continue;

				data[x][y] = selectedColor;
				filledPixels.add(p);

				if (x > 0 && data[x - 1][y] == oriColor)
					buffer.add(new Point(x - 1, y));
				if (x < data.length - 1 && data[x + 1][y] == oriColor)
					buffer.add(new Point(x + 1, y));
				if (y > 0 && data[x][y - 1] == oriColor)
					buffer.add(new Point(x, y - 1));
				if (y < data[0].length - 1 && data[x][y + 1] == oriColor)
					buffer.add(new Point(x, y + 1));
			}
			paintPanel.repaint();
		}

		// Sending the list of pixels for bucket paint
		try {
			out.writeInt(2);
			out.writeInt(filledPixels.size());
			out.writeInt(selectedColor);
			for (Point p : filledPixels) {
				out.writeInt(p.x);
				out.writeInt(p.y);
			}
			out.flush();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return filledPixels;
	}

	/**
	 * set pixel data and block size
	 *
	 * @param data
	 * @param blockSize
	 */
	public void setData(int[][] data, int blockSize) {
		this.data = data;
		this.blockSize = blockSize;
		paintPanel.setPreferredSize(new Dimension(data.length * blockSize, data[0].length * blockSize));
		paintPanel.repaint();
	}
}
