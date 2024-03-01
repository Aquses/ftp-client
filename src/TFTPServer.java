package assignment3;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;

public class TFTPServer {
	public static final int TFTPPORT = 4970;
	public static final int BUFSIZE = 516;
	public static final String READDIR = "/home/username/read/"; //custom address at your PC
	public static final String WRITEDIR = "/home/username/write/"; //custom address at your PC
	// OP codes
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
						
						System.out.printf("%s request for %s from %s using port %d\n",
								(reqtype == OP_RRQ)?"Read":"Write",
								clientAddress.getHostName(), clientAddress.getPort());  
								
						// Read request
						if (reqtype == OP_RRQ) {      
							requestedFile.insert(0, READDIR);
							HandleRQ(sendSocket, requestedFile.toString(), OP_RRQ);
						}
						// Write request
						else {                       
							requestedFile.insert(0, WRITEDIR);
							HandleRQ(sendSocket,requestedFile.toString(),OP_WRQ);  
						}
						sendSocket.close();
					} 
					catch (SocketException e) {
						e.printStackTrace();
					}
				}
			}.start();
		}
	}
	
	/**
	 * Reads the first block of data, i.e., the request for an action (read or write).
	 * @param socket (socket to read from)
	 * @param buf (where to store the read data)
	 * @return socketAddress (the socket address of the client)
	 */
	private InetSocketAddress receiveFrom(DatagramSocket socket, byte[] buf) {
		// Create datagram packet
		DatagramPacket packet = new DatagramSocket(buf, buf.length);
		
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
		if(opcode == OP_RRQ) {
			// See "TFTP Formats" in TFTP specification for the DATA and ACK packet contents
			boolean result = send_DATA_receive_ACK(params);
		}
		else if (opcode == OP_WRQ) {
			boolean result = receive_DATA_send_ACK(params);
		}
		else {
			System.err.println("Invalid request. Sending an error packet.");
			// See "TFTP Formats" in TFTP specification for the ERROR packet contents
			send_ERR(params);
			return;
		}		
	}
	
	/**
	To be implemented
	*/
	private boolean send_DATA_receive_ACK(DatagramSocket clientSocket, String requestedFile) {

		FileInputStream fis = new FileInputStream(requestedFile);
		BufferedInputStream bis = new BufferedInputStream(fis);
		byte[] buffer = new byte[BUFSIZE];
		int bytesRead;


		while((bytesRead = bis.read(buffer)) != -1) {

		}


		return true;
	}

	private void send_DATA(DatagramSocket socket, byte[] data, int length, int blockNum) {
		
	}
	
	private boolean receive_DATA_send_ACK() {
		final int BUFSIZE = 516;

		try {
			// Received packets.
			byte[] buf = new byte[BUFSIZE];
			DatagramPacket receivedPacket = new DatagramPacket(buf, buf.length);
			socket.receive(receivedPacket);

			if (receivedPacket.getLength() == 0) {
				
			}



		} catch (IOException e){
			// TODO: send_ERR
		}

		return true;
	}
	
	private void send_ERR() {

	}
	
}