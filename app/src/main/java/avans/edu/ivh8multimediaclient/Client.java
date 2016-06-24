package avans.edu.ivh8multimediaclient;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.ImageView;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.OutputStreamWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.StringTokenizer;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by Jeffrey on 24-5-2016.
 */
public class Client {
    // RTP variables:
    DatagramPacket rcvdp; // UDP packet received from the server
    DatagramSocket RTPsocket; // socket to be used to send and receive UDP packets
    static int RTP_RCV_PORT = 25000; // port where the client will receive the RTP packets

    Timer timer; // timer used to receive data from the UDP socket
    byte[] buf; // buffer used to store data received from the server
    Bitmap bitmap; // The bitmap which contains the images for the video

    // RTSP variables
    // RTSP States
    final static int INIT = 0;
    final static int READY = 1;
    final static int PLAYING = 2;
    static int state; // RTSP state == INIT or READY or PLAYING
    Socket RTSPsocket; // socket used to send/receive RTSP messages
    // Input and output stream filters
    static BufferedReader RTSPBufferedReader;
    static BufferedWriter RTSPBufferedWriter;
    static String VideoFileName; // video file to request to the server
    int RTSPSeqNb = 0; // Sequence number of RTSP messages within the session
    int RTSPid = 0; // ID of the RTSP session (given by the RTSP Server)
    int currentImage = 0; // Used to update the progressbar

    VideoScreen videoScreen;
    Handler mainThreadHandler;

    final static String CRLF = "\r\n"; // To end header lines

    // Video constants:
    static int MJPEG_TYPE = 26; // RTP payload type for MJPEG video

    /**
     * The constructor of the client which will build the GUI elements
     */
    public Client(VideoScreen videoScreenParam) {
        this.videoScreen = videoScreenParam;
        new Thread() {
            @Override
            public void run() {
                initialize();
                enableSetupBtn();
            }
        }.start();

        buf = new byte[15000];
    }

    private void initialize() {
        try {
            // Get server RTSP port and IP address from the command line
            int RTSP_server_port = 4444; // Integer.parseInt(argv[1]);
            String ServerHost = "192.168.0.100"; // argv[0];
            InetAddress ServerIPAddr = InetAddress.getByName(ServerHost);

            // Get video filename to request
            VideoFileName = "movie.Mjpeg"; // argv[2];

            // Establish a TCP connection with the server to exchange RTSP messages
            RTSPsocket = new Socket(ServerIPAddr, RTSP_server_port);
            Log.d("VIVZ", "CLIENT CREATED A RTSP SOCKET WITH PORT NUMBER: " + RTSPsocket.getPort());

            // Set input and output stream filters:
            RTSPBufferedReader = new BufferedReader(new InputStreamReader(RTSPsocket.getInputStream()));
            RTSPBufferedWriter = new BufferedWriter(new OutputStreamWriter(RTSPsocket.getOutputStream()));

            // Initialize RTSP state:
            state = INIT;
            //
        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /*
     * This method will handle the SETUP button click via RTSP
     * Will setup a RTP connection with the server
     */
    public void handleSetupRequest() {
        Log.d("VIVZ", "Setup Button pressed!");

        if (state == INIT) {
            new Thread() {
                @Override
                public void run() {
                    // Initialize non-blocking RTPsocket that will be used to receive data
                    try {
                        // Construct a new DatagramSocket (UDP) to receive RTP packets
                        Log.d("VIVZ", "before rtp socket");
                        RTPsocket = new DatagramSocket(RTP_RCV_PORT); // Setting up port for the client himself on port number 25000
                        Log.d("VIVZ", "CLIENT CREATED A RTP SOCKET WITH PORT NUMBER: " + RTPsocket.getLocalPort());

                        //If no data arrives within 5ms a SocketException will be thrown
                        RTPsocket.setSoTimeout(5);
                    } catch (SocketException se) {
                        System.out.println("Socket exception: " + se);
                        System.exit(0);
                    }

                    // Initialize RTSP sequence number
                    RTSPSeqNb = 1;

                    // Send SETUP message to the server
                    send_RTSP_request("SETUP");

                    // Wait for the response
                    if (parse_server_response() != 200)
                        System.out.println("Invalid Server Response");
                    else {
                        // Change RTSP state and print new state
                        state = READY;
                        enableControlBtns();
                        disableSetupBtn();
                        System.out.println("New RTSP state: READY");
                    }
                }
            }.start();
        } // else if state != INIT then do nothing
    }

    /*
     * This method will handle the PLAY button click via RTSP
     */
    public void handlePlayRequest() {
        System.out.println("Play Button pressed !");

        if (state == READY) {
            new Thread() {
                @Override
                public void run() {
                    // Increase RTSP sequence number
                    RTSPSeqNb++;

                    // Send PLAY message to the server
                    send_RTSP_request("PLAY");

                    // Wait for the response
                    if (parse_server_response() != 200)
                        System.out.println("Invalid Server Response");
                    else {
                        // Change RTSP state and print out new state
                        state = PLAYING;
                        System.out.println("New RTSP state: PLAYING");

                        // Start the timer and sending action events to listeners
                        //timer.start();
                        startTimer();
                    }
                }
            }.start();
        } // else if state != READY then do nothing
    }

    /*
     * This method will handle clicking on the pause button
     */
    public void handlerPauseRequest() {
        System.out.println("Pause Button pressed !");

        if (state == PLAYING) {
            new Thread() {
                @Override
                public void run() {
                    RTSPSeqNb++;

                    // Send PAUSE message to the server
                    send_RTSP_request("PAUSE");

                    // Wait for the response
                    if (parse_server_response() != 200)
                        System.out.println("Invalid Server Response");
                    else {
                        // Change RTSP state and print out new state
                        state = READY;
                        System.out.println("New RTSP state: ...");

                        // Stop the timer
                        timer.cancel();
                    }
                }
            }.start();
        } // else if state != PLAYING then do nothing
    }

    /*
     * This method will handle clicking on the tear down button
     */
    public void handlerTearDownRequest() {
        new Thread() {
            @Override
            public void run() {
                System.out.println("Teardown Button pressed !");
                RTSPSeqNb++;

                // Send TEARDOWN message to the server
                send_RTSP_request("TEARDOWN");

                // Wait for the response
                if (parse_server_response() != 200)
                    System.out.println("Invalid Server Response");
                else {
                    // change RTSP state and print out new state
                    state = INIT;
                    System.out.println("New RTSP state: ...");

                    // Stop the timer
                    timer.cancel();

                    // Exit the system
                    System.exit(0);
                }
            }
        }.start();
    }

    /*
     * The receivePacket() method will be invoked by the timer
     * continuously until the timer stops.
     */
    public void receivePacket() {
        new Thread() {
            @Override
            public void run() {
                Log.d("VIVZ", "receivePacket() called!");
                // Construct a DatagramPacket to receive data from the UDP socket
                // The data will be put in the buffer (buf) which has a length of buf.lenght
                rcvdp = new DatagramPacket(buf, buf.length);

                try {
                    // Receive the DataPacket from the socket with the video data from the socket
                    RTPsocket.receive(rcvdp);

                    // Create an RTPpacket object from the DataPacket
                    RTPpacket rtp_packet = new RTPpacket(rcvdp.getData(), rcvdp.getLength());
                    currentImage = rtp_packet.getsequencenumber();
                    Log.d("VIVZ", "RTP packet image number: " + rtp_packet.getsequencenumber());

                    // Print header bit stream:
                    rtp_packet.printheader();

                    // Get the payload bitstream from the RTPpacket object
                    int payload_length = rtp_packet.getpayload_length(); // Will be 26 in this case because the type was 26: MJPEG
                    System.out.println("Payload is: " + payload_length);
                    byte[] payload = new byte[payload_length];
                    rtp_packet.getpayload(payload);

                    bitmap = BitmapFactory.decodeByteArray(payload, 0, payload_length);
                } catch (InterruptedIOException iioe) {
                    System.out.println("Exception caught: " + iioe);
                } catch (IOException ioe) {
                    System.out.println("Exception caught: " + ioe);
                }
            }
        }.start();
    }

    /*
     * This method will simply read the server response and
     * will filter the status code out of it. For example 200 for OK
     * and 400 for error and will return this status code
     */
    private int parse_server_response() {
        int reply_code = 0;

        try {
            // Read the status line which is the first line in the header reponse from the server.
            // So in this case this first line could be: RTSP/1.0 200 OK. When you call Read line again,
            // It will move to the second line and so on.
            String StatusLine = RTSPBufferedReader.readLine();
            System.out.println(StatusLine);

            // The tokenizer will split a string based on spaces between the words
            StringTokenizer tokens = new StringTokenizer(StatusLine);
            tokens.nextToken(); // Skip over the RTSP version (RTSP/1.0)
            reply_code = Integer.parseInt(tokens.nextToken()); //Get the code which is 200 (so the OK is avoided)

            // If reply code is OK get and print the 2 other lines
            if (reply_code == 200) {
                // Now read the second line, which could be: CSeq: 1 for example.
                String SeqNumLine = RTSPBufferedReader.readLine();
                System.out.println(SeqNumLine);

                // Read the third line which could be: Session: 123456 for example.
                String SessionLine = RTSPBufferedReader.readLine();
                System.out.println(SessionLine);

                // If state == INIT gets the Session Id from the SessionLine
                tokens = new StringTokenizer(SessionLine);
                tokens.nextToken(); // Skip over the Session:
                RTSPid = Integer.parseInt(tokens.nextToken()); //Will be 123456 in this case
            }
        } catch (Exception ex) {
            System.out.println("Exception caught: " + ex);
            System.exit(0);
        }

        return (reply_code);
    }

    /*
     * This method will send a RTSP request to the server
     */
    private void send_RTSP_request(String request_type) {
        Log.d("VIVZ", "send_RTSP_request() called");
        try {

            RTSPBufferedWriter.write(request_type + " " + VideoFileName + " " + "RTSP/1.0" + CRLF);
            RTSPBufferedWriter.write("CSeq: " + RTSPSeqNb + CRLF);

            if (request_type.equals("SETUP")) {
                RTSPBufferedWriter.write("Transport: RTP/UDP; client_port= " + RTP_RCV_PORT + CRLF);
            } else {
                RTSPBufferedWriter.write("Session: " + RTSPid + CRLF);
            }

            // Flushes the output stream and forces any buffered output bytes to be written out
            RTSPBufferedWriter.flush();
        } catch (Exception ex) {
            System.out.println("Exception caught: " + ex);
        }
    }

    /**
     * This method will start a timer which will call the
     * drawFrame() method every 20 ms.
     */
    private void startTimer() {
        //Setup and start timer
        timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                receivePacket();
                drawFrame();
                updateProgressBar();
            }
        }, 0, 20);
    }

    /**
     * This method will enable the buttons on the main UI
     * through the handler of the main thread. The buttons
     * will only enable themselves when a RTSP connection was successful
     */
    public void enableSetupBtn() {
        Log.d("VIVZ", "enableButtons called!");
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            public void run() {
                videoScreen.enableSetupBtn();
            }
        });
    }

    public void enableControlBtns() {
        Log.d("VIVZ", "enableButtons called!");
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            public void run() {
                videoScreen.enableControlBtns();
            }
        });
    }

    public void disableSetupBtn() {
        Log.d("VIVZ", "enableButtons called!");
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            public void run() {
                videoScreen.disableSetupBtn();
            }
        });
    }

    /**
     * This method will draw a frame on the main UI
     * through the handler of the main thread.
     */
    public void drawFrame() {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                videoScreen.drawFrame(bitmap);
            }
        });
    }

    /**
     * This method will update the progress bar on the main UI
     * through the handler of the main thread.
     */
    public void updateProgressBar() {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                videoScreen.updateProgressBar(currentImage);
            }
        });
    }

    //To update the UI and set the frame on the screen
    public interface VideoScreen {
        public void drawFrame(Bitmap bitmap);

        public void enableSetupBtn();

        public void enableControlBtns();

        public void disableSetupBtn();

        public void updateProgressBar(int progress);
    }
}// end of Class Client


