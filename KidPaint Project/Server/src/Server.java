import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Server {
	ServerSocket serverSocket;
	DatagramSocket udpSocket;


	Map<Integer, List<Integer>> sketchData = new HashMap<>();

	Map<Integer, List<Socket>> list = new HashMap<>();

	public Server() throws IOException {
		int port = 12345;
		serverSocket = new ServerSocket(port);
		udpSocket = new DatagramSocket(55555);

		Thread t = new Thread(() -> {
			byte[] buffer = new byte[1024];
			DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
			while (true) {
				try {
					udpSocket.receive(packet);
					byte[] res = (InetAddress.getLocalHost().getHostAddress() + "," + port).getBytes();
					DatagramPacket reply = new DatagramPacket(res, res.length, packet.getAddress(), packet.getPort());
					udpSocket.send(reply);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		});
		t.start();

		System.out.println("Listening at port : " + port);
		while (true) {
			Socket clientSocket = serverSocket.accept();

			Thread t2 = new Thread(() -> {
				int studioNumber = 0;

				try {
					sendStudios(clientSocket);
					
					DataInputStream in = new DataInputStream(clientSocket.getInputStream());
					studioNumber = in.readInt();

					// if new studio is created
					list.computeIfAbsent(studioNumber, k -> new ArrayList<>()).add(clientSocket);

					if (sketchData.containsKey(studioNumber)) {
						sendSketchData(clientSocket, sketchData.get(studioNumber));
					}

					serve(clientSocket, studioNumber);

				} catch (IOException e) {
					e.printStackTrace();
				} finally {
					if (studioNumber != 0) {
						list.get(studioNumber).remove(clientSocket);
					}
				}

			});
			t2.start();
		}

	}
	
	private void sendStudios(Socket clientSocket) throws IOException {
		DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream());
		out.writeInt(3);
		out.writeInt(list.size());
		for (int i = 0; i < list.size(); i++) {
			out.writeInt(i+1);
		}
		out.flush();
	}

	private void sendSketchData(Socket clientSocket, List<Integer>data) throws IOException {

		DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream());
		int numberOfMessages = data.size() / 3;
		out.writeInt(numberOfMessages);
		
		for (int i = 0; i < numberOfMessages; i++) {
			out.writeInt(1); // drawing message

			int color = data.get(i * 3);
			int x = data.get(i * 3 + 1);
			int y = data.get(i * 3 + 2);

			out.writeInt(color); // color
			out.writeInt(x); // x
			out.writeInt(y); // y

		}
		out.flush();
	}

	private void serve(Socket clientSocket, int studioNumber) throws IOException {
		DataInputStream in = new DataInputStream(clientSocket.getInputStream());

		while (true) {
			try {
			int type = in.readInt(); // type represents the message type

			switch (type) {
			case 0:
				// text message
				forwardTextMessage(in, studioNumber);
				break;
			case 1:
				// drawing message;
				forwardDrawingMessage(in, studioNumber);
				break;
			case 2:
				// bucket message
				forwardBucketMessage(in, studioNumber);
				break;
			case 3:
				clearSketch(in, studioNumber);
			default:
				// others
			}
			} catch (EOFException ex) {
				break;
			}
		}
	}

	private void forwardTextMessage(DataInputStream in, int studioNumber) throws IOException {
		byte[] buffer = new byte[1024];

		int nameLen = in.readInt();
		in.read(buffer, 0, nameLen);

		int len = in.readInt();
		in.read(buffer, nameLen, nameLen + len);
		
		List<Socket> clients = list.get(studioNumber);
		
		synchronized (clients) {
			for (int i = 0; i < clients.size(); i++) {
				try {
					Socket s = clients.get(i);

					DataOutputStream out = new DataOutputStream(s.getOutputStream());
					out.writeInt(0);
					out.writeInt(nameLen + len);
					out.write(buffer, 0, nameLen + len);
					out.flush();
				} catch (IOException ex) {
					System.out.println("Client already disconnected");
				}

			}
		}

	}

	@SuppressWarnings("rawtypes")
	private void forwardDrawingMessage(DataInputStream in, int studioNumber) throws IOException {
		int color = in.readInt();
		int x = in.readInt();
		int y = in.readInt();
		System.out.printf("%d @(%d, $d)\n", color, x, y);

		// to store the sketch data
		sketchData.computeIfAbsent(studioNumber, k -> new ArrayList()).addAll(Arrays.asList(color,x,y));

		List<Socket> clients = list.get(studioNumber);
		
		synchronized (clients) {
			for (int i = 0; i < clients.size(); i++) {
				Socket s = clients.get(i);
				DataOutputStream out = new DataOutputStream(s.getOutputStream());
				out.writeInt(1);
				out.writeInt(color);
				out.writeInt(x);
				out.writeInt(y);
				out.flush();
			}
		}

	}

	private void forwardBucketMessage(DataInputStream in, int studioNumber) throws IOException {
		int size = in.readInt();
		int color = in.readInt();
		int[] x = new int[size];
		int[] y = new int[size];
		for (int i = 0; i < size; i++) {
			x[i] = in.readInt();
			y[i] = in.readInt();
		}

		for (int i = 0; i < size; i++) {
			sketchData.computeIfAbsent(studioNumber, k -> new ArrayList()).addAll(Arrays.asList(color,x[i],y[i]));

		}
		
		List<Socket> clients = list.get(studioNumber);

		synchronized (clients) {
			for (int i = 0; i < clients.size(); i++) {
				Socket s = clients.get(i);
				DataOutputStream out = new DataOutputStream(s.getOutputStream());
				out.writeInt(2);
				out.writeInt(size);
				out.writeInt(color);
				for (int j = 0; j < size; j++) {
					out.writeInt(x[j]);
					out.writeInt(y[j]);
				}
				out.flush();
			}
		}

	}
	
	private void clearSketch(DataInputStream in, int studioNumber) throws IOException {
		int size = in.readInt();
		int color = in.readInt();
		int[] x = new int[size];
		int[] y = new int[size];
		for (int i = 0; i < size; i++) {
			x[i] = in.readInt();
			y[i] = in.readInt();
		}

		for (int i = 0; i < size; i++) {
			sketchData.computeIfAbsent(studioNumber, k -> new ArrayList()).addAll(Arrays.asList(color,x[i],y[i]));

		}
		
		List<Socket> clients = list.get(studioNumber);

		synchronized (clients) {
			for (int i = 0; i < clients.size(); i++) {
				Socket s = clients.get(i);
				DataOutputStream out = new DataOutputStream(s.getOutputStream());
				out.writeInt(2);
				out.writeInt(size);
				out.writeInt(color);
				for (int j = 0; j < size; j++) {
					out.writeInt(x[j]);
					out.writeInt(y[j]);
				}
				out.flush();
			}
		}

	}

	public static void main(String[] args) throws IOException {
		// TODO Auto-generated method stub
		new Server();

	}

}