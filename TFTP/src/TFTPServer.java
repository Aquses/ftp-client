package src;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.PortUnreachableException;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class TFTPServer {
	public static final int TFTPPORT = 4970;
	public static final int BUFSIZE = 516; // 512
	public static final String READDIR = "/home/kenzo/Desktop/student/assignment3/read/"; //custom address at your PC # CHANGE HERE
	public static final String WRITEDIR = "/home/kenzo/Desktop/student/assignment3/write/"; //custom address at your PC # CHANGE HERE
	// OP codes
	// 1     Read request (RRQ)
	// 2     Write request (WRQ)
	// 3     Data (DATA)
	// 4     Acknowledgment (ACK)
	// 5     Error (ERROR)
	public static final int OP_RRQ = 1;
	public static final int OP_WRQ = 2;
	public static final int OP_DAT = 3;
	public static final int OP_ACK = 4;
	public static final int OP_ERR = 5;

	public static void main(String[] args) {
		if (args.length > 0) {
			System.err.printf("usage: java %s\n", TFTPServer.class.getCanonicalName());
			System.exit(1);
		}
		//Starting the server
		try {
			TFTPServer server= new TFTPServer();
			server.start();
		}
		catch (SocketException e) {
			e.printStackTrace();
		}
	}
	
	private void start() throws SocketException {
		try {
			byte[] buf= new byte[BUFSIZE];
			// Create socket
			DatagramSocket socket= new DatagramSocket(null);
			
			// Create local bind point 
			SocketAddress localBindPoint= new InetSocketAddress(TFTPPORT);
			socket.bind(localBindPoint);

			System.out.printf("Listening at port %d for new requests\n", TFTPPORT);

			// Loop to handle client requests 
			while (true) {        
				
				final InetSocketAddress clientAddress = receiveFrom(socket, buf);
				
				// If clientAddress is null, an error occurred in receiveFrom()
				if (clientAddress == null) 
					continue;

				final StringBuffer requestedFile= new StringBuffer();
				final int reqtype = ParseRQ(buf, requestedFile);

				new Thread() {
					public void run() {
						try {
							DatagramSocket sendSocket= new DatagramSocket(0);

							// Connect to client
							sendSocket.connect(clientAddress);						
							
							System.out.printf("%s request from %s using port %d\n",
									(reqtype == OP_RRQ)?"Read":"Write",
									clientAddress.getHostName(), clientAddress.getPort());
					 
							// Read request

							if (reqtype == OP_RRQ) {      
								requestedFile.insert(0, READDIR);
								HandleRQ(sendSocket, requestedFile.toString(), OP_RRQ);
							}
							// Write request
							else if (reqtype == OP_WRQ){                       
								requestedFile.insert(0, WRITEDIR);
								HandleRQ(sendSocket,requestedFile.toString(),OP_WRQ);  
							}
							else {
								HandleRQ(sendSocket, requestedFile.toString(), (int)reqtype);
							}
							
							sendSocket.close();
						} 
						catch (SocketException e) {
							e.printStackTrace();
						}
					}
				}.start();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Reads the first block of data, i.e., the request for an action (read or write).
	 * @param socket (socket to read from)
	 * @param buf (where to store the read data)
	 * @return socketAddress (the socket address of the client)
	 */
	private InetSocketAddress receiveFrom(DatagramSocket socket, byte[] buf) throws IOException {
		// Create datagram packet
		DatagramPacket packet = new DatagramPacket(buf, buf.length);
		// Receive packet
		socket.receive(packet);
		// Get client address and port from the packet
		InetSocketAddress socketAddress = new InetSocketAddress(packet.getAddress(), packet.getPort());

		return socketAddress;
	}

	/**
	 * Parses the request in buf to retrieve the type of request and requestedFile
	 * 
	 * @param buf (received request)
	 * @param requestedFile (name of file to read/write)
	 * @return opcode (request type: RRQ or WRQ)
	 */
	private int ParseRQ(byte[] buf, StringBuffer requestedFile) {
		// See "TFTP Formats" in TFTP specification for the RRQ/WRQ request contents
		ByteBuffer wrap = ByteBuffer.wrap(buf);
		short opcode = wrap.getShort();

		byte[] fileNameBytes = new byte[buf.length - 2];
		wrap.get(fileNameBytes);
		requestedFile.append(new String(fileNameBytes));
		
		return opcode;
	}


	/**
	 * Handles RRQ and WRQ requests 
	 * 
	 * @param sendSocket (socket used to send/receive packets)
	 * @param requestedFile (name of file to read/write)
	 * @param opcode (RRQ or WRQ)
	 */
	private void HandleRQ(DatagramSocket sendSocket, String requestedFile, int opcode) {
		String[] request = requestedFile.split("\0");
		String fileName = request[0];

		
		File file = new File(fileName);
		
		// See "TFTP Formats" in TFTP specification for the DATA and ACK packet contents
		if (opcode == OP_RRQ) {
			if (!(file.exists() && file.isFile())) {
				send_ERR(sendSocket, (short) 1, "File not found.");
			}
			send_DATA_receive_ACK(sendSocket, fileName);
		}
		else if (opcode == OP_WRQ) {
			if (file.exists() && file.isFile()) {
				send_ERR(sendSocket, (short) 6, "File already exists.");
			}
			receive_DATA_send_ACK(sendSocket, fileName);
		}
		else {
			// See "TFTP Formats" in TFTP specification for the ERROR packet contents
			send_ERR(sendSocket, (short) 4, "Illegal TFTP operation.");
			return;
		}		
	}
	
	private boolean send_DATA_receive_ACK(DatagramSocket clientSocket, String requestedFile) {
		try {
			final int MAX_RETRIES = 5; // counter for blocks sending
			int readBytes;
			short blockNum = 1;						
			byte[] buffer = new byte[BUFSIZE];
			boolean doneErr = false;
			FileInputStream fis = new FileInputStream(requestedFile);
			
			do {
				readBytes = fis.read(buffer);
				int i = buffer.length - 1;
				while (i >= 0 && buffer[i] == 0) {
					--i;
				}
				buffer = Arrays.copyOf(buffer, i + 1);

				sendAndReceive(clientSocket, buffer, blockNum, MAX_RETRIES, doneErr);
				blockNum++;
			} while ((readBytes != -1) && !doneErr);
			fis.close();
		} catch (IOException e) {
			e.printStackTrace();
			send_ERR(clientSocket, (short) 0, "Error in file reading or socket communication.");
		}
		return true;
	}
	
	private void sendAndReceive(DatagramSocket clientSocket, byte[] buffer, short blockNum, int maxRetries, boolean doneErr) {
		// Send data to the client
		sendDataPacket(clientSocket, buffer, blockNum);
	
		// Receive Acknowledgment.
		if (receiveAck(clientSocket, blockNum)) {
			blockNum++;
		} else {
			// If we do not receive acknowledgment from client.
			if (!receiveAck(clientSocket, blockNum)) {
				int retryCount = 0;
				while (retryCount < maxRetries) {
					sendDataPacket(clientSocket, buffer, blockNum);
	
					if (receiveAck(clientSocket, blockNum)) {
						blockNum++;
						break;
					}
					retryCount++;
	
					if (retryCount >= maxRetries) {
						send_ERR(clientSocket, (short)0, "Server does not receive or we do not receive.");
						doneErr = true;
						break;
					}
				}
			}
		}
	}
	
	// reference: https://www.geeksforgeeks.org/bytebuffer-put-methods-in-java-with-examples-set-1/
	private void sendDataPacket(DatagramSocket socket, byte[] data, short blockNum) {
		try {
			ByteBuffer bufferArray = ByteBuffer.allocate(4 + data.length);
			bufferArray.putShort((short) OP_DAT); // adds operation code into the packet
			bufferArray.putShort(blockNum); // adds the block number into the packet
			bufferArray.put(data); // copies and transfers data into the packet
	
			DatagramPacket packet = new DatagramPacket(bufferArray.array(), bufferArray.position(), socket.getRemoteSocketAddress());
			socket.send(packet);
		} catch (IOException e) {
			send_ERR(socket, (short) 0, "Could not send the DAT packet.");
		}
	}
	

	private boolean receiveAck(DatagramSocket socket, short expectedBlock) {
		byte[] buffer = new byte[4];
		DatagramPacket receivedAck = new DatagramPacket(buffer, buffer.length);
		ByteBuffer bufferArray = ByteBuffer.wrap(buffer);

		try {
			socket.receive(receivedAck);
		} catch (PortUnreachableException e) {
			send_ERR(socket, (short) 0, "Port Unreachable.");
			return false;
		} catch (IOException e) {
			send_ERR(socket, (short) 0, "Error while receiving acknowledgment.");
			return false;
		}

		short opcode = bufferArray.getShort();
		// 4     Acknowledgment (ACK)
		// 5     Error (ERROR)
		switch (opcode) {
			case OP_ACK:
				short receivedBlock = bufferArray.getShort();
				if (receivedBlock == expectedBlock) {
					return true;
				} else {
					send_ERR(socket, (short) 0, "Received ACK with incorrect block number.");
					return false;
				}		
			case OP_ERR:
				send_ERR(socket, (short) 0, "The ACK was not received.");
				return false;
			default:
				send_ERR(socket, (short) 0, "Incorrect opcode received.");
				return false;
		}
	}

	private boolean receive_DATA_send_ACK(DatagramSocket socket, String requestedFile) {
		try {
			FileOutputStream fos = new FileOutputStream(requestedFile);
			short block = 0;
			short opcode = 0;
			int receivedData = 512;
	
			sendAckPacket(socket, block);
			while (receivedData >= 512) {
				byte[] data = new byte[BUFSIZE];
				DatagramPacket receive = new DatagramPacket(data, BUFSIZE);
				socket.receive(receive);
	
				ByteBuffer dataBuffer = ByteBuffer.wrap(data);
				opcode = dataBuffer.getShort();
				block = dataBuffer.getShort();
	
				switch (opcode) {
					case OP_DAT:
						fos.write(dataBuffer.array(), 4, receive.getLength() - 4);
						receivedData = receive.getLength();
						sendAckPacket(socket, block);
						block++;
						break;  // Continue receiving data
					case OP_ERR:
						send_ERR(socket, (short) 0, "Error.");
						return false;
					default:
						send_ERR(socket, (short) 0, "Invalid opcode.");
						return false;
				}
			}
			fos.close();  // Close the file after receiving all data
		} catch (IOException e) {
			send_ERR(socket, (short) 0, "Error in file writing or socket communication.");
			return false;
		}
		return true;
	}
	

	private void sendAckPacket(DatagramSocket socket, short expectedBlock) {
		try {
			ByteBuffer bufferArray = ByteBuffer.allocate(4);
			bufferArray.putShort((short) OP_ACK); // adds operation code into the packet
			bufferArray.putShort(expectedBlock); // adds the block number into the packet
	
			DatagramPacket packet = new DatagramPacket(bufferArray.array(), bufferArray.position(), socket.getRemoteSocketAddress());
			socket.send(packet);
		} catch (IOException e) {
			send_ERR(socket, (short) 0, "Could not send the ACK packet.");
		}
	}
	
	private void send_ERR(DatagramSocket socket, short errorCode, String errorMessage) {
		// 0  Not defined, see error message (if any).
		// 1  File not found.
		// 2  Access violation.
		// 3  Disk full or allocation exceeded.
		// 4  Illegal TFTP operation.
		// 5  Unknown transfer ID.
		// 6  File already exists.
		// 7  No such user.
		int bufferSize = 5 + errorMessage.getBytes().length;
		ByteBuffer bufferArr = ByteBuffer.allocate(bufferSize);
		bufferArr.putShort((short) OP_ERR);
		bufferArr.putShort(errorCode);
		bufferArr.put(errorMessage.getBytes());
		bufferArr.put((byte) 0);
	
		// Send error message
		DatagramPacket errorPacket = new DatagramPacket(bufferArr.array(), bufferArr.array().length);
		try {
			socket.send(errorPacket);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}	
}