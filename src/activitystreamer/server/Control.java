package activitystreamer.server;

import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import activitystreamer.util.Settings;
import activitystreamer.util.WaitingMessage;

public class Control extends Thread {
	private static final Logger log = LogManager.getLogger();
	private static ArrayList<Connection> connections;
	private static boolean term = false;
	private static Listener listener;
	private static Map<String, String> userList;
	private static Map<String, Integer> remoteList;
	private static ArrayList<Connection> serversList;
	private static Map<String, Integer> checkRemoteList;
	private static Map<String, Integer> sequenceList;
	private static Map<String, Integer> sequenceList0;
	private static String serverSecret = Settings.getSecret();
	private static String id = Settings.nextSecret();
	private static String localhost = Settings.getLocalHostname();
	private static int localport = Settings.getLocalPort();
	public static int load = 0;
	private static ArrayList<WaitingMessage> waitings;
	//private static ArrayList<String> loginList;
	private JSONParser parser = new JSONParser();

	protected static Control control = null;

	public static Control getInstance() {
		if (control == null) {
			control = new Control();
		}
		return control;
	}

	public Control() {
		// initialize the connections array
		connections = new ArrayList<Connection>();
		userList = new HashMap<String, String>();
		remoteList = new HashMap<String, Integer>();
		serversList = new ArrayList<Connection>();
		waitings = new ArrayList<WaitingMessage>();
		checkRemoteList = new HashMap<String, Integer>();
		sequenceList = new HashMap<String, Integer>();
		sequenceList0 = new HashMap<String, Integer>();
		

		// start a listener
		try {
			listener = new Listener();
		} catch (IOException e1) {
			log.fatal("failed to startup a listening thread: " + e1);
			System.exit(-1);
		}
		start();
	}

	@SuppressWarnings("unchecked")
	public void initiateConnection() {
		// make a connection to another server if remote hostname is supplied
		if (Settings.getRemoteHostname() != null) {
			try {
				outgoingConnection(new Socket(Settings.getRemoteHostname(),
						Settings.getRemotePort()));
				// zhenyuan
				JSONObject outgoingObj = new JSONObject();
				outgoingObj.put("command", "AUTHENTICATE");
				outgoingObj.put("secret", serverSecret);
				connections.get(0).writeMsg(outgoingObj.toJSONString());
				serversList.add(connections.get(0));
				// zhenyuan

			} catch (IOException e) {
				log.error("failed to make connection to "
						+ Settings.getRemoteHostname() + ":"
						+ Settings.getRemotePort() + " :" + e);
				System.exit(-1);
			}
		} else {
			serverSecret = Settings.nextSecret();
			log.info("\n\nserver secret is: " + serverSecret+"\nuse this secret to launch other servers\n");
		}
	}

	/*
	 * Processing incoming messages from the connection. Return true if the
	 * connection should close.
	 */
	@SuppressWarnings("unchecked")
	public synchronized boolean process(Connection con, String msg) {
		// zhenyuan
		try {
			JSONObject incomingObj = (JSONObject) this.parser.parse(msg);
			cmdOperator(con, incomingObj); // xueyang
		} catch (Exception e) {
			log.error("invalid JSON object");
			JSONObject outgoingObj = new JSONObject();
			outgoingObj.put("command", "INVALID_MESSAGE");
			outgoingObj.put("info", "JSON parse error while parsing message");
			con.writeMsg(outgoingObj.toJSONString());
			control.connectionClosed(con);
			con.closeCon();
		}
		// zhenyuan
		return false;
	}

	// zhenyuan&xueyang
	@SuppressWarnings("unchecked")
	public static void cmdOperator(Connection con, JSONObject incomingObj) {
		JSONObject outgoingObj = null;

		if (incomingObj.containsKey("command")) {
			String cmd = (String) incomingObj.get("command");
			switch (cmd) {
			case "LOGIN":
				log.info("\n\na client is trying to log in\n");
				if (ifLogin(incomingObj)) {
					load=connections.size()-serversList.size();
					outgoingObj = loginSuccess(incomingObj);
					redirect(con, incomingObj);
				} else {
					outgoingObj = loginFail(incomingObj);
				}
				con.writeMsg(outgoingObj.toJSONString());
				break;
			case "REGISTER":
				log.info("\n\na client is trying to register\n");
				register(con, incomingObj);
				break;
			case "LOGOUT":
				log.info("\n\na client is trying to logout\n");
				/**
				 * if(!incomingObj.get("username").toString().equals("anonymous")) {
				 * loginList.remove(incomingObj.get("username").toString()); }
				 **/
				control.connectionClosed(con); // remove connection
				con.closeCon();
				load=connections.size()-serversList.size();
				log.info("connection size is :"+connections.size());
				break;
			case "AUTHENTICATE":
				log.info("\n\na server is trying to authenticate\n");
				serversList.add(con);
				log.info("authentication success for new server!");									 // ��ѩ��
				outgoingObj=authenticateSuccess(incomingObj);
				log.info("sent authenticate success message!");	
				con.writeMsg(outgoingObj.toJSONString());											   // ��ѩ��
				if (authenticateFail(incomingObj)) {
					log.info("aithentication fail");
					outgoingObj = authenticateReply(incomingObj);
					con.writeMsg(outgoingObj.toJSONString());
					serversList.remove(con);
					control.connectionClosed(con); // remove connection
					con.closeCon();
				}
				break;
			case "AUTHENTICATION_SUCCESS":										 // ��ѩ��
				log.info("I receive AUTHENTICATION_SUCCESS message!\n");			 // ��ѩ��
				int mySequence = Integer.parseInt(incomingObj.get("sequence").toString());					 // ��ѩ��
				Settings.setSequence(mySequence);									 // ��ѩ��
				log.info("My sequence is: "+mySequence);								 // ��ѩ��
				if (incomingObj.containsKey("userList")) {
				log.info("my pre "+userList);
				userList = StringToMap(incomingObj.get("userList").toString());
				}
				log.info("my userList is "+userList);
				break;																 // ��ѩ��
			case "AUTHTENTICATION_FAIL":
				log.info("\n\nauthentication fail!\n");
				serversList.remove(con);
				control.connectionClosed(con); // remove connection
				con.closeCon();
				break;
			case "SERVER_ANNOUNCE":
				
				String incomingHostname = incomingObj.get("hostname").toString();
				String incomingPort =incomingObj.get("port").toString();
				String incomingLoad = incomingObj.get("load").toString();
				String incomingSequence = incomingObj.get("sequence").toString();
				remoteList.put(incomingHostname + ":"+ incomingPort, Integer.valueOf(incomingLoad));
				sequenceList.put(incomingHostname + ":"+ incomingPort, Integer.valueOf(incomingSequence));
				
				log.info("\n\nincoming server announce: \nhostname of incoming server is "+ incomingHostname +
						 "\nport of incoming server is "+incomingPort+"\nload of incoming server is "+incomingLoad+"\n");
				
				for (int i = 0; i < connections.size(); i++) {
					if (i != connections.indexOf(con)
							&& serversList.contains(connections.get(i))) {
						log.info(connections.get(i));
						connections.get(i)
								.writeMsg(incomingObj.toJSONString());
					}
				}
				break;

			case "ACTIVITY_MESSAGE":
				log.info("\n\na client is trying to send activity message\n");
				activityMessage(con, incomingObj);
				break;
			case "ACTIVITY_BROADCAST":
				outgoingObj = incomingObj;
				for (int i = 0; i < connections.size(); i++) {
					if (i != connections.indexOf(con)) {
						connections.get(i)
								.writeMsg(outgoingObj.toJSONString());
					}
				}
			case "LOCK_REQUEST":
				lockReply(con, incomingObj);
				break;
			case "LOCK_ALLOWED":
				lockAllowed(con, incomingObj);
				break;
			case "LOCK_DENIED":
				lockDenied(con, incomingObj);
				break;
			case "REDIRECT_REQUEST":
				log.info("received Redirect request");
				redirectCheck(con, incomingObj);
				break;
			case "SEQUENCE_UPDATE":
			    log.info("received Sequence update message!");
			    int sequence = Integer.parseInt(incomingObj.get("sequence").toString());     
			    Settings.setSequence(sequence+1);
			    outgoingObj = new JSONObject();
			    outgoingObj.put("command", "SEQUENCE_UPDATE");
			    outgoingObj.put("sequence", Settings.getSequence());
			    for (int i = 0; i < serversList.size(); i++) {
			    	if (i != serversList.indexOf(con)) {
			    		serversList.get(i).writeMsg(outgoingObj.toJSONString());
			    	}
			    }
			    break;
			default:
				outgoingObj = new JSONObject();
				outgoingObj.put("command", "INVALID_MESSAGE");
				outgoingObj.put("info", "No command " + cmd);
				con.writeMsg(outgoingObj.toJSONString());
				if (serversList.contains(con)) {
					serversList.remove(con);
				}
				control.connectionClosed(con); // remove connection
				con.closeCon();
				break;
			}
		} else {
			outgoingObj = new JSONObject();
			outgoingObj.put("command", "INVALID_MESSAGE");
			outgoingObj.put("info", "the received message did not contain a command");
			con.writeMsg(outgoingObj.toJSONString());

		}

	}


	@SuppressWarnings("unchecked")
	public synchronized static void lockDenied(Connection con,
			JSONObject incomingObj) {
		JSONObject outgoingObj = new JSONObject();
		String username = (String) incomingObj.get("username");
		String secret = (String) incomingObj.get("secret");

		if (userList.containsKey(username)
				&& userList.get(username).equals(secret))
			userList.remove(username);

		for (WaitingMessage temp : waitings)
			if (temp.getKey().equals(username + secret)) {
				if (serversList.contains(temp.getConnection())) {
					outgoingObj.put("command", "LOCK_DENIED");
					outgoingObj.put("username", username);
					outgoingObj.put("secret", secret);
				} else {
					outgoingObj.put("command", "REGISTER_FAILED");
					outgoingObj.put("info", "register success for" + username);
				}
				temp.getConnection().writeMsg(outgoingObj.toJSONString());

				for (Connection tempCon : serversList) {
					if (!tempCon.equals(con))
						tempCon.writeMsg(outgoingObj.toJSONString());
				}
				waitings.remove(temp);
				break;
			}
	}

	//
	@SuppressWarnings("unchecked")
	public synchronized static void lockAllowed(Connection con,
			JSONObject incomingObj) {
		String username = (String) incomingObj.get("username");
		String secret = (String) incomingObj.get("secret");
		for (WaitingMessage temp : waitings) {
			if (temp.getKey().equals(username + secret)) {
				temp.setNum();
				if (temp.compare()) {
					JSONObject outgoingObj = new JSONObject();
					if (serversList.contains(temp.getConnection())) {
						outgoingObj.put("command", "LOCK_ALLOWED");
						outgoingObj.put("username", username);
						outgoingObj.put("secret", secret);
						userList.put(username, secret);
					} else {
						log.info("\n\nregister success!\n");
						outgoingObj.put("command", "REGISTER_SUCCESS");
						outgoingObj.put("info",
								"register success for " + username);
						temp.getConnection()
								.writeMsg(outgoingObj.toJSONString());
						JSONObject loginObj = new JSONObject();
						log.info("\n\nlogin success!\n");
						loginObj.put("command", "LOGIN_SUCCESS");
						loginObj.put("info", "logged in as user " + username);
						temp.getConnection().writeMsg(loginObj.toJSONString());
						userList.put(username, secret);
						log.info("falg master");
						redirect(temp.getConnection(), incomingObj);
					}
					waitings.remove(temp);
					break;
				}
			}
		}
	}

	// zhenyuan&xueyang

	public static boolean ifLogin(JSONObject incomingObj) {
		boolean successLogin = false;
		String username = (String) incomingObj.get("username");
		String secret = (String) incomingObj.get("secret");
		
		if (username.equals("anonymous")) {
			successLogin = true;
		} else if (userList.containsKey(username)) {
			if (userList.get(username).equals(secret)) {
			successLogin = true;
			}
		}
		return successLogin;
	}

	// zhenyuan
	@SuppressWarnings("unchecked")
	public static JSONObject loginSuccess(JSONObject incomingObj) {

		JSONObject outgoingObj = new JSONObject();
		String username = (String) incomingObj.get("username");

		log.info("\n\nlogin success!\n");
		outgoingObj.put("command", "LOGIN_SUCCESS");
		outgoingObj.put("info", "logged in as user " + username);
		//loginList.add(username);
		return outgoingObj;
	}
	// zhenyuan

	// zhenyuan
	@SuppressWarnings("unchecked")
	public static JSONObject loginFail(JSONObject incomingObj) {

		JSONObject outgoingObj = new JSONObject();

		outgoingObj.put("command", "LOGIN_FAILED");
		outgoingObj.put("info", "attempt to login with wrong secret");

		return outgoingObj;
	}
	// zhenyuan

	// zhenyuan
	@SuppressWarnings("unchecked")
	public static void redirect(Connection con, JSONObject incomingObj) {
		JSONObject outgoingObj = new JSONObject();
		for (String key : remoteList.keySet()) {
			if (remoteList.get(key).intValue() < load - 1) {
				log.info("redirect!");
				//if (!incomingObj.get("username").toString()
				//		.equals("anonymous")) {
					//loginList.remove(incomingObj.get("username").toString());
				//}
				outgoingObj.put("command", "REDIRECT");
				outgoingObj.put("hostname",
						key.substring(0, key.indexOf(":")));
				outgoingObj.put("port",
						Integer.valueOf(key.substring(key.indexOf(":") + 1)));
				con.writeMsg(outgoingObj.toJSONString());
				connections.remove(con);
				con.closeCon();
				load=connections.size()-serversList.size();
				log.info("remote load is " + remoteList.get(key));
				log.info("load is " + load);
				break;
			}
		}

	}
	// zhenyuan

	// zhenyuan
	@SuppressWarnings("unchecked")
	public static void register(Connection con, JSONObject incomingObj) {
		JSONObject outgoingObj = new JSONObject();
		String username = (String) incomingObj.get("username");
		String secret = (String) incomingObj.get("secret");

		if (!userList.containsKey(username)) {
			if (serversList.size() == 0) {
				userList.put(username, secret);
				log.info("\n\nregister success!\n");
				outgoingObj.put("command", "REGISTER_SUCCESS");
				outgoingObj.put("info", "register success for" + username);
				con.writeMsg(outgoingObj.toJSONString());

			} else {
				userList.put(username, secret);
				outgoingObj.put("command", "LOCK_REQUEST");
				outgoingObj.put("username", username);
				outgoingObj.put("secret", secret);
				waitings.add(new WaitingMessage(con, "LOCK_REQUEST",
						username + secret, serversList.size()));
				for (int i = 0; i < serversList.size(); i++) {
					serversList.get(i).writeMsg(outgoingObj.toJSONString());
				}
			}
		} else {
			outgoingObj.put("command", "REGISTER_FAILED");
			outgoingObj.put("info",
					username + " is already registered with the system");
			con.writeMsg(outgoingObj.toJSONString());
		}
	}
	// zhenyuan
	
	@SuppressWarnings("unchecked")
	public static JSONObject authenticateSuccess(JSONObject incomingObj) {						// ��ѩ��
		JSONObject outgoingObj = new JSONObject();												// ��ѩ��
		outgoingObj.put("command","AUTHENTICATION_SUCCESS");                                   // ��ѩ��
		int UrSequence = Settings.getSequence()+1;
		outgoingObj.put("sequence", UrSequence);                                       // ��ѩ��	
		if (userList.size() != 0) {
		outgoingObj.put("userList", userList.toString());
		}
		return outgoingObj;																		// ��ѩ��
	}
	
	public static boolean authenticateFail(JSONObject incomingObj) {
		String secret = (String) incomingObj.get("secret");
		return !secret.equals(serverSecret);
	}

	@SuppressWarnings("unchecked")
	public static JSONObject authenticateReply(JSONObject incomingObj) {
		JSONObject outgoingObj = new JSONObject();
		String secret = (String) incomingObj.get("secret");
		outgoingObj.put("command", "AUTHENTICATION_FAIL");
		outgoingObj.put("info", "the supplied secret is incorrect: " + secret);
		return outgoingObj;
	}

	@SuppressWarnings("unchecked")
	public static void activityMessage(Connection con,
			JSONObject incomingObj) {
		JSONObject outgoingObj = new JSONObject();
		boolean successLogin = false;
		String username = (String) incomingObj.get("username");
		String secret = (String) incomingObj.get("secret");
		// String activity = (String) incomingObj.get("activity");

		if (username.equals("anonymous")) {
			successLogin = true;
		} else if (/** loginList.contains(username) && **/
		userList.get(username).equals(secret)) {
			successLogin = true;
		}

		if (successLogin) {
			outgoingObj.put("command", "ACTIVITY_BROADCAST");
			JSONObject activityObj = (JSONObject) incomingObj.get("activity");
			if(activityObj.containsKey("authenticated_user")) {
				activityObj.remove("authenticated_user");
			}
			activityObj.put("authenticate_user", username);
			outgoingObj.put("activity", activityObj);
			for (int i = 0; i < connections.size(); i++) {
					connections.get(i).writeMsg(outgoingObj.toJSONString());
			}
		} else {
			outgoingObj.put("command", "AUTHENTICATION_FAIL");
			outgoingObj.put("info", "user did not login");
			con.writeMsg(outgoingObj.toJSONString());
			con.closeCon();
		}
	}

	@SuppressWarnings("unchecked")
	public static void lockReply(Connection con, JSONObject incomingObj) {
		JSONObject outgoingObj = new JSONObject();
		String username = (String) incomingObj.get("username");
		String secret = (String) incomingObj.get("secret");

		if (userList.containsKey(username)) {
			outgoingObj.put("command", "LOCK_DENIED");
			outgoingObj.put("username", username);
			outgoingObj.put("secret", secret);
			con.writeMsg(outgoingObj.toJSONString());
		} else {
			if (serversList.size() == 1) {
				outgoingObj.put("command", "LOCK_ALLOWED");
				outgoingObj.put("username", username);
				outgoingObj.put("secret", secret);
				userList.put(username, secret);
				con.writeMsg(outgoingObj.toJSONString());
			} else {
				waitings.add(new WaitingMessage(con, "LOCK_REQUEST",
						username + secret, serversList.size() - 1));
				for (int i = 0; i < serversList.size(); i++)
					if (serversList.get(i) != con)
						serversList.get(i)
								.writeMsg(incomingObj.toJSONString());
			}
		}
	}

	@SuppressWarnings("unchecked")
	public static void redirectCheck(Connection con, JSONObject incomingObj) {
		String receiverHost = (String) incomingObj.get("receiverHost");
		Integer receiverPort = Integer.valueOf(incomingObj.get("receiverPort").toString());
		String senderHost = (String) incomingObj.get("senderHost");
		Integer senderPort = Integer.valueOf(incomingObj.get("senderPort").toString());
		if (receiverHost.equals(localhost) && receiverPort.intValue() == localport) {
			Connection aClient = null;
			// TODO: check if aClient can be null.
			for(int i = 0; i < connections.size(); i++) {
				if(!serversList.contains(connections.get(i))) {
					aClient = connections.get(i);
					log.info("aClient=connection.getiִ����");
				}
			}
			log.info("Time to write REDIRECT message to client");
			JSONObject outgoingObj = new JSONObject();
			outgoingObj.put("command", "REDIRECT");
			outgoingObj.put("hostname", senderHost);
			outgoingObj.put("port", senderPort);
			aClient.writeMsg(outgoingObj.toJSONString());
			log.info("before close connection");
			connections.remove(aClient);
			aClient.closeCon();
			load=connections.size()-serversList.size();		
		}else {
			for(int i = 0; i < serversList.size(); i++) {
				if(i != serversList.indexOf(con)) {
					serversList.get(i).writeMsg(incomingObj.toJSONString());
				}
			}
		}
		
	}
	
	
	/*
	 * The connection has been closed by the other party.
	 */
	public synchronized void connectionClosed(Connection con) {
		if (!term)
			connections.remove(con);
	}

	/*
	 * A new incoming connection has been established, and a reference is returned
	 * to it
	 */
	public synchronized Connection incomingConnection(Socket s)
			throws IOException {
		log.debug("\n\nincomming connection: " + Settings.socketAddress(s)+"\n");
		Connection c = new Connection(s);
		connections.add(c);
		return c;

	}

	/*
	 * A new outgoing connection has been established, and a reference is returned
	 * to it
	 */
	public synchronized Connection outgoingConnection(Socket s)
			throws IOException {
		log.debug("\n\noutgoing connection: " + Settings.socketAddress(s)+"\n");
		Connection c = new Connection(s);
		connections.add(c);
		return c;
	}
	

	@SuppressWarnings("unchecked")
	@Override
	public void run() {
		log.info("\n\nusing activity interval of " + Settings.getActivityInterval()
				+ " milliseconds\n");
		while (!term) {
			// do something with 5 second intervals in between

			// zhenyuan
			JSONObject outgoingObj = new JSONObject();
			outgoingObj.put("command", "SERVER_ANNOUNCE");
			outgoingObj.put("id", id);
			outgoingObj.put("load", load);
			outgoingObj.put("hostname", localhost);
			outgoingObj.put("port", localport);
			outgoingObj.put("sequence", Settings.getSequence());
			for (int i = 0; i < connections.size(); i++) {
				if (serversList.contains(connections.get(i))) {
					connections.get(i).writeMsg(outgoingObj.toJSONString());
					log.info("\n\noutgoing server announce: \nhostname of this server is "+ Settings.getLocalHostname() +
							 "\nport of this server is "+Settings.getLocalPort()+"\nload of this server is "+load+"\n");
				}
			}
			// zhenyuan
			
			// sequence_update
			if (Settings.getSequence() == 0) {
			    JSONObject sequenceObj = new JSONObject();
			    sequenceObj.put("command", "SEQUENCE_UPDATE");
			    sequenceObj.put("sequence", Settings.getSequence());
			    for (int i = 0; i < connections.size(); i++) {
			    	if (serversList.contains(connections.get(i))) {
			    		connections.get(i).writeMsg(sequenceObj.toJSONString());
			    	}
			    }
			}
			//
			
			//zhenyuan
			JSONObject redirectObj = new JSONObject();
			redirectObj.put("command", "REDIRECT_REQUEST");
			redirectObj.put("senderHost", localhost);
			redirectObj.put("senderPort", new Integer(localport));
			String receiverKey = null; 
			for (String key : sequenceList.keySet()) {
				log.info("For logout redirection: remote load is " + remoteList.get(key));
				log.info("For logout redirection: My load is " + load);
				log.info("My sequence is "+ Settings.getSequence());
				if (remoteList.get(key).intValue() > load + 1) {
					receiverKey = key;
				}
			}
			if(receiverKey != null) {
				System.out.println("send request to redirect");
				log.info("send request to redirect");
				redirectObj.put("receiverHost",receiverKey.substring(0, receiverKey.indexOf(":")));
				redirectObj.put("receiverPort", Integer.valueOf(receiverKey.substring(receiverKey.indexOf(":") + 1)));
				
				for(int i = 0; i < serversList.size(); i++) {
					serversList.get(i).writeMsg(redirectObj.toJSONString());
				}
			//zhenyuan	
				
			}
			
			if(sequenceList.size() !=0) {
				for(String key : sequenceList.keySet()) {
					checkRemoteList.put(key, sequenceList.get(key));
				}
			}
			
			
			Integer minSequence = Settings.getSequence();
			String address = Settings.getLocalHostname()+":"+Settings.getLocalPort();
			
			//TODO: check the judgment of this if clause.
			if( checkRemoteList.size() !=0 && !sequenceList.containsValue(0) && sequenceList0.equals(sequenceList)) {
				
				boolean reconnect = false;
				
				for (String key : sequenceList.keySet()) {
					if(minSequence < sequenceList.get(key)) {
						minSequence = sequenceList.get(key);
					}
				}
				
				if(minSequence == Settings.getSequence()) {
					log.info("1");
					if(Settings.getSequence() != 1) {
						log.info("2");
						for(String key : checkRemoteList.keySet()) {
							log.info("3");
							log.info(key);
							log.info(Settings.getRemoteHostname()+":"+Settings.getRemotePort());
		
							if(!reconnect /*&& !key.equals(Settings.getRemoteHostname()+":"+Settings.getRemotePort())*/
									&& checkRemoteList.get(key) == minSequence)  {
								
									log.info("redirect to this server");
								
								Settings.setRemoteHostname(key.substring(0, key.indexOf(":")));
								Settings.setRemotePort(Integer.parseInt(key.substring(key.indexOf(":") + 1)));
								try {
									Connection c = outgoingConnection(new Socket(Settings.getRemoteHostname(),
											Settings.getRemotePort()));
							
									JSONObject authenticateObj = new JSONObject();
									authenticateObj.put("command", "AUTHENTICATE");
									authenticateObj.put("secret", serverSecret);
									c.writeMsg(authenticateObj.toJSONString());
									serversList.add(c);
									reconnect = true;
									checkRemoteList = new HashMap<String, Integer>();


								} catch (IOException e) {
									log.error("failed to make connection to "
											+ Settings.getRemoteHostname() + ":"
											+ Settings.getRemotePort() + " :" + e);
									
								}
							}					
						
						}
						
						if(!reconnect && minSequence > 1) {
							log.info("4");
							
							for(String key : checkRemoteList.keySet()) {
								if(!reconnect && checkRemoteList.get(key) == minSequence - 1) {
									//redirect to this server
									
									Settings.setRemoteHostname(key.substring(0, key.indexOf(":")));
									Settings.setRemotePort(Integer.parseInt(key.substring(key.indexOf(":") + 1)));
									try {
										Connection c = outgoingConnection(new Socket(Settings.getRemoteHostname(),
												Settings.getRemotePort()));
								
										JSONObject authenticateObj = new JSONObject();
										authenticateObj.put("command", "AUTHENTICATE");
										authenticateObj.put("secret", serverSecret);
										c.writeMsg(authenticateObj.toJSONString());
										serversList.add(c);
										reconnect = true;
										checkRemoteList = new HashMap<String, Integer>();


									} catch (IOException e) {
										log.error("failed to make connection to "
												+ Settings.getRemoteHostname() + ":"
												+ Settings.getRemotePort() + " :" + e);
										
									}
									
									
								}
							}
							
						}
						if(!reconnect && minSequence > 1) {
							log.info("4");
							
							for(String key : checkRemoteList.keySet()) {
								if(!reconnect && checkRemoteList.get(key) == minSequence - 2) {
									//redirect to this server
									
									Settings.setRemoteHostname(key.substring(0, key.indexOf(":")));
									Settings.setRemotePort(Integer.parseInt(key.substring(key.indexOf(":") + 1)));
									try {
										Connection c = outgoingConnection(new Socket(Settings.getRemoteHostname(),
												Settings.getRemotePort()));
								
										JSONObject authenticateObj = new JSONObject();
										authenticateObj.put("command", "AUTHENTICATE");
										authenticateObj.put("secret", serverSecret);
										c.writeMsg(authenticateObj.toJSONString());
										serversList.add(c);
										reconnect = true;
										checkRemoteList = new HashMap<String, Integer>();


									} catch (IOException e) {
										log.error("failed to make connection to "
												+ Settings.getRemoteHostname() + ":"
												+ Settings.getRemotePort() + " :" + e);
										
									}
									
									
								}
							}
							
						}
						if(!reconnect && minSequence > 1) {
							log.info("4");
							
							for(String key : checkRemoteList.keySet()) {
								if(!reconnect && checkRemoteList.get(key) == minSequence - 3) {
									//redirect to this server
									
									Settings.setRemoteHostname(key.substring(0, key.indexOf(":")));
									Settings.setRemotePort(Integer.parseInt(key.substring(key.indexOf(":") + 1)));
									try {
										Connection c = outgoingConnection(new Socket(Settings.getRemoteHostname(),
												Settings.getRemotePort()));
								
										JSONObject authenticateObj = new JSONObject();
										authenticateObj.put("command", "AUTHENTICATE");
										authenticateObj.put("secret", serverSecret);
										c.writeMsg(authenticateObj.toJSONString());
										serversList.add(c);
										reconnect = true;
										checkRemoteList = new HashMap<String, Integer>();


									} catch (IOException e) {
										log.error("failed to make connection to "
												+ Settings.getRemoteHostname() + ":"
												+ Settings.getRemotePort() + " :" + e);
										
									}
									
									
								}
							}
							
						}
					
					}else {
						log.info("The initial server has crushed or disconnected");
						
						if(checkRemoteList.containsValue(1)) {
							log.info("5");
							for(String key : checkRemoteList.keySet()) {
								if(!reconnect && checkRemoteList.get(key) == 1 && address.compareTo(key)<0)  {
									address = key;
								}
							}
							
							if( !address.equals(Settings.getLocalHostname()+":"+Settings.getLocalPort())) {
								log.info("6");
								
								Settings.setRemoteHostname(address.substring(0, address.indexOf(":")));
								Settings.setRemotePort(Integer.parseInt(address.substring(address.indexOf(":") + 1)));
								try {
									Connection c = outgoingConnection(new Socket(Settings.getRemoteHostname(),
											Settings.getRemotePort()));
							
									JSONObject authenticateObj = new JSONObject();
									authenticateObj.put("command", "AUTHENTICATE");
									authenticateObj.put("secret", serverSecret);
									c.writeMsg(authenticateObj.toJSONString());
									serversList.add(c);
									reconnect = true;
									checkRemoteList = new HashMap<String, Integer>();
	
	
								} catch (IOException e) {
									log.error("failed to make connection to "
											+ Settings.getRemoteHostname() + ":"
											+ Settings.getRemotePort() + " :" + e);
									
								}
							}else {
								log.info("7");
								Settings.setSequence(0);
							}
							
							
						}else {
							log.info("8");
							Settings.setSequence(0);
						}
						
					}
					
					
				}
			}
			
			
			/*
			if(serversList.size() == 0 && sequenceList.isEmpty() && Settings.getSequence() != 0) {
				log.info("9");
				initiateConnection();
			}*/
			
			/*
			if(serversList.size() == 0 && sequenceList.isEmpty() && Settings.getSequence() == 0 && remoteList.size() !=0 ) {
				log.info("10");
				Random rnd = new Random();
			    int randomNum = rnd.nextInt(remoteList.size());
			    ArrayList<String> keys = new ArrayList<String>(remoteList.keySet());
			    String rndKey = keys.get(randomNum);
				Settings.setRemoteHostname(rndKey.substring(0, rndKey.indexOf(":")));
				Settings.setRemotePort(Integer.parseInt(rndKey.substring(rndKey.indexOf(":") + 1)));
				try {
					Connection c = outgoingConnection(new Socket(Settings.getRemoteHostname(),
							Settings.getRemotePort()));
			
					//TODO: check
					JSONObject authenticateObj1 = new JSONObject();
					authenticateObj1.put("command", "AUTHENTICATE");
					authenticateObj1.put("secret", serverSecret);
					c.writeMsg(authenticateObj1.toJSONString());
					serversList.add(c);
					checkRemoteList = new HashMap<String, Integer>();


				} catch (IOException e) {
					log.error("failed to make connection to "
							+ Settings.getRemoteHostname() + ":"
							+ Settings.getRemotePort() + " :" + e);
				}
			}*/
			
			sequenceList0 = sequenceList; sequenceList = new HashMap<String, Integer>();
			
			try {
				Thread.sleep(Settings.getActivityInterval());
			} catch (InterruptedException e) {
				log.info("\n\nreceived an interrupt, system is shutting down\n");
				break;
			}
			if (!term) {

				term = doActivity();
			}

		}
		log.info("\nclosing " + connections.size() + " connections");
		// clean up
		for (Connection connection : connections) {
			connection.closeCon();
		}
		listener.setTerm(true);
	}
	
	public static Map<String, String> StringToMap(String str){
		Map<String, String> map = new HashMap<String, String>();
		str = str.substring(1, str.length()-1);
		int k = str.length() - str.replaceAll(",","").length();
		for(int i = 0; i <= k; i++) {
			map.put(str.split(",")[i].split("=")[0],str.split(",")[i].split("=")[1]);
		}
		return map;
	}

	public boolean doActivity() {
		return false;
	}

	public final void setTerm(boolean t) {
		term = t;
	}

	public final ArrayList<Connection> getConnections() {
		return connections;
	}
	
	public final ArrayList<Connection> getServerConnections() {
		return serversList;
	}
	
	public static void removeServersList(Connection con) {
		  serversList.remove(con);
	 }
}
