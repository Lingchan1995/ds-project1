Distributed System Project1

------------------------------------------------------------------------------
Project information
------------------------------------------------------------------------------

This project is for COMP90015 Distributed System Project1, 2018 SM1

Team members:
Linghan Zhou
Xueyang Ding
Chenfeng Liao
Zhenyuan Ye

------------------------------------------------------------------------------
File information
------------------------------------------------------------------------------

Jar file list:

ActivityStreamerClient.jar, ActivityStreamerServer.jar

------------------------------------------------------------------------------

1. client part
  Client.java -> the main function entrance of client front-end;
  
  Clientskelecton.java -> the funtion to deal with the messages recieved 
                          and sent and response actions to the received 
                          messages;
						  
  TextFrame.java -> the graphic user interface development part provided to 
                    the client users to write activity message, send and 
                    disconnecting after connecting to selected server.

------------------------------------------------------------------------------

2. server part
   Server.java -> the main function entrance of server-end;
   
   Connection.java -> a defined data structure that provide the pattern of 
                      connections between servers and servers to clients, 
                      which contains the operation functions of connecting, 
                      disconnecting,writing message and running 
					  state screening;
					  
   Listener.java -> the listening function to listen the connection 
                    requirement from servers and clients and send the message
                    to the server the listener belongs to;
					
   Control.java -> this class helps to maintain the runing state of matching
                   servers, which contains the functions of message dealing 
                   including the responses to register, authentication, login,
                   activity broadcast, lock request and reply and matching 
				   operations for the responses.

   WaitingMessage.java -> a defined data structure that provide the message 
                          pattern stored in the waiting queue which used in 
                          lock situations while registering to matching the
						  recieved lock messages.

------------------------------------------------------------------------------

3.	other part
   Settings.java -> a class that defines the initialization settings of	
                    clients and servers, also contains the functions including
                    getting information part and setting information.

------------------------------------------------------------------------------
How to start
------------------------------------------------------------------------------

1. client

demo:

    Command (register):
    java -jar ActivityStreamerClient.jar -rh 127.0.0.1 -rp 12345 -u username

    Command (anonymous login):
    java -jar ActivityStreamerClient.jar -rh 127.0.0.1 -rp 12345

    Command (login):
    java -jar ActivityStreamerClient.jar -rh 127.0.0.1 -rp 12345 -u username
    -s secret

Set register and login operations at command line with cli options, -rh value 
means setting the ip address of remote server that the client is going to 
connect; -rp value means setting the remote port number on the erver that the
client is going to connect; -u means setting the username, -s means setting 
the secret, a combination of username and secret is used to verify the whether
the user is allowed to connect the server.

For example, -rh 178.18.9.16 -rp 2173 -u username1 -s secret1 means the client
is going to connect server whose ip adress is 178.18.9.16 at port 2173, with 
the username of username1 and secret secret1. If the username1 and secret1 
combination exist in the server 178.18.9.16, the client will connect to the 
server and the client-end will provide a GUI to send json-pattern activity
message.

After the GUI appearance, users can write activity messages with json pattern
in windows, then click send-button, the message will be send to all servers
and clients connected in the system and the message recieved by the client 
will also appear in the window. While click the disconnect button, the 
connection between the client and the server connected will be closed.

The value of rh and rp must be setted before connection, but the value of 
username and secret is not necessary, if users wanna login anonymously, users
can not set the value of username or set it with "anonymous"; also, if users
set username without "anonymous" but do not set the value of secret, the 
server will register with the username first and then the client will try to 
login. A client secret will be generated randomly and shown on the command
line.

------------------------------------------------------------------------------

2. server

demo:

    Command (launch the first server):
    java -jar ActivityStreamerServer.jar -lh 127.0.0.1 -lp 12345

    Command (launch other servers):
    java -jar ActivityStreamerServer.jar -lh 127.0.0.1 -lp 12345 
    -rh 127.0.0.1 -rp 12346 -s secret

In the command line, the input -lh indidates setting the value of localhost,
which is the hostname of this server, -lp for localport (port of this server),
-rh for remotehost (server hostname it will connect to) and -rp for remoteport
(server port it will connect to).

For the first server in the system, only the -lh and -lp should be indicated
because there is no server it can connect. Then a random secret will be
generated, shown on command window. That secret will be used for all other
servers to authenticate.

For all the other servers, not only -lh & -lp but also -rh, -rp and -s should
be indicated. Servers will automaticallly connect to another server and
authenticate when launching.

------------------------------------------------------------------------------
How to use
------------------------------------------------------------------------------

1. client

demo;

input:
    {"hello":"world"}

output:
    {
    "command" : "ACTIVITY_BOARDCAST",
    "activity" : {
                  "authenticated_user" : "anonymous",
                  "hello" : "world"
                 }
    }

When starting to run, the client will try to connect the a selected server. 
After successfully connect to the server, a GUI window for the client will
automatically appear to provide a entrance to send activity messages. Users
can write messages on the GUI window and click the send button, then all the 
connected clients and servers including the sender will receive a responsing
activity_broadcast message. Notice all the message should be written in json
pattern, like demo above. So that the system can recognize the information, 
or the system will refuse to send the wrong-format message. 

If users want to end the connections between the client and the server 
connected, clicking disconnect button will make sense, but the  relavent GUI 
will never be automatically closed. 

*** Warning ***
While a client connecting to the servers is redirected to another server, 
the redirection operation will be done automatically, and the GUI window of
the old connection will show the redirect message, but the window will never
be closed automatically.

------------------------------------------------------------------------------

2. server

Servers online should not be concerned, since we assume they never go down.

------------------------------------------------------------------------------
