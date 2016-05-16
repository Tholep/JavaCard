package initiate;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.math.BigInteger;

import java.util.List;
import javax.swing.*;

import javax.crypto.Cipher;
//import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import java.security.*;
//import java.security.spec.*;
//import java.security.interfaces.*;s
import java.security.SecureRandom;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;

import javax.smartcardio.*;

import java.util.*;



/**
 * Terminal for the Personalization phase of the E-Purse applet.
 */
public class personTerminal extends JPanel implements ActionListener {

	static final int BLOCKSIZE = 128;
	
	static final String TITLE = "Initiate E-Purse";
	static final int DISPLAY_WIDTH = 30;
	static final int DISPLAY_HEIGHT = 15;
	static final int AMOUNT_WIDTH = 30;
	static final int AMOUNT_HEIGHT = 1;
    static final Font FONT = new Font("Monospaced", Font.BOLD, 24);

	static final byte[] APPLET_AID = {(byte) 0x3B,(byte) 0x29,(byte) 0x63,(byte) 0x61,(byte) 0x6C,(byte) 0x63,(byte) 0x01};


	static final CommandAPDU SELECT_APDU = new CommandAPDU((byte) 0x00,(byte) 0xA4,(byte) 0x04,(byte) 0x00,APPLET_AID);

	private static final byte CLA_WALLET = (byte) 0xCC;
	private static final byte INS_ISSUE = (byte) 0x40;
	private static final byte INS_KEY = (byte) 0x41;
	private static final byte INS_ID = (byte) 0x42;
	private static final byte INS_BLDT = (byte) 0x43;
	private static final byte INS_MODULUS = (byte) 0x44;
	private static final byte INS_EXP = (byte) 0x45;

    private static byte[] masterkey = null ;
    
	Cipher ecipher;

	/** GUI stuff. */
	JTextArea display;
	JPanel button;

	/** GUI stuff. */
	JButton issueButton,keyButton;

	/** The card applet. */
	CardChannel applet;

	/**
	 * Constructs the terminal application.
	 */
	public personTerminal(JFrame parent) {
		buildGUI(parent);
		setEnabled(false);
		addActionListener(this);
		(new CardThread()).start();
	}

	/**
	 * Builds the GUI.
	 */
	void buildGUI(JFrame parent) {
		setLayout(new BorderLayout());
		display = new JTextArea(DISPLAY_HEIGHT, DISPLAY_WIDTH);
		display.setEditable(false);
		add(new JScrollPane(display), BorderLayout.NORTH);
		
		issueButton = new JButton("Issue");
        button = new JPanel(new FlowLayout());
		button.add(issueButton);

	    add(button, BorderLayout.SOUTH);
		
	    parent.addWindowListener(new CloseEventListener());		
	}
    
	/**
	 * Adds the action listener <code>l</code> to all buttons.
	 * 
	 * @param l
	 *            the action listener to add.
	 */
	public void addActionListener(ActionListener l) {
		issueButton.addActionListener(l);
	}

	class CardThread extends Thread {
		public void run() {
			try {
				TerminalFactory tf = TerminalFactory.getDefault();
				CardTerminals ct = tf.terminals();
				List<CardTerminal> cs = ct.list(CardTerminals.State.CARD_PRESENT);
				while (cs.isEmpty()) {
					display.setText("Insert a Card!");
					sleep(1000);
					display.setText("");
					sleep(1000);
					cs = ct.list(CardTerminals.State.CARD_PRESENT);
				}

				while (true) {
					try {
						for (CardTerminal c : cs) {
							if (c.isCardPresent()) {
								try {
									Card card = c.connect("*");
									try {
										applet = card.getBasicChannel();
										ResponseAPDU resp = applet.transmit(SELECT_APDU);
										if (resp.getSW() != 0x9000) {
											throw new Exception("Select failed");
										}
																			
										/** Wait for the card to be removed */
										while (c.isCardPresent())
											;

										break;
									} catch (Exception e) {
										display.setText("Card does not contain E-purse Applet!");
										sleep(2000);
										continue;
									}
								} catch (CardException e) {
									display.setText("Couldn't connect to card!");
									sleep(2000);
									continue;
								}
							} else {
								display.setText("Insert a Card!");
								sleep(1000);
								display.setText("");
								sleep(1000);
								continue;
							}
						}
					} catch (CardException e) {
						display.setText("Card status problem!");
					}
				}
			} catch (Exception e) {
				setEnabled(false);
				display.setText("ERROR: " + e.getMessage());
				e.printStackTrace();
			}			
		}
	}

	/**
	 * Handles button events.
	 * 
	 * @param ae
	 *            event indicating a button was pressed.
	 */
	public void actionPerformed(ActionEvent ae) {
		try {
			Object src = ae.getSource();
			if (src instanceof JButton) {
				issue();
			}
		} catch (Exception e) {
			System.out.println("ERROR: " + e.getMessage());
		}
	}
	
	/**
	 * Handles 'issue' button event.
	 * 
	 * @throws CardException
	 *             if something goes wrong.
	 */
	
	void issue() throws CardException {
		try {
			CommandAPDU capdu = new CommandAPDU(CLA_WALLET, INS_ISSUE,(byte) 0, (byte) 0);
			ResponseAPDU rapdu = applet.transmit(capdu);
			if (rapdu.getSW() != 0x9000) {
				display.append("The card has been already issued\n");
				issueButton.setEnabled(false);
			} else{
				/** Reads the master key from the file */
			    File file = new File("key");
			    masterkey = new byte[(int) file.length()];
			    try {
			       FileInputStream fileInputStream = new FileInputStream(file);
			       try {
					fileInputStream.read(masterkey);
				   } catch (IOException e) {
				        System.out.println("Error Reading The File.\n");
			        	e.printStackTrace();
				   }	
				} catch (FileNotFoundException e) {
		          		System.out.println("File Not Found.\n");
		          		e.printStackTrace();
		        }
	        
				/** Generates the ID of the card **/
				SecureRandom random = new SecureRandom();
			    byte ID[] = new byte[16];
			    random.nextBytes(ID);

			    /** Saves the ID of the card to a file with all the IDs */
				File file1 = new File("cards_id.txt");

				if (!file1.exists()) {
					file1.createNewFile();
				}

				FileWriter fw = new FileWriter(file1.getAbsoluteFile(), true);
				BufferedWriter bw = new BufferedWriter(fw);
				bw.write(toHexString(ID));
				bw.newLine();
				bw.close();

			    /** Creates the key of the card  {ID}k_m */
			    SecretKey key_m = new SecretKeySpec(masterkey, 0, masterkey.length, "AES");
			    byte[] key_ID = encrypt(ID,key_m);			    
			    
			    /** Sends the key to the card */
				CommandAPDU capdu2 = new CommandAPDU(CLA_WALLET, INS_KEY,(byte) 0,(byte) 0,key_ID, BLOCKSIZE);
				applet.transmit(capdu2);
				
				/** Sends the id to the card */
				CommandAPDU capdu3 = new CommandAPDU(CLA_WALLET, INS_ID,(byte) 0,(byte) 0,ID, BLOCKSIZE);
				applet.transmit(capdu3);
				
				/**Generates the sign key pair */
				KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
				generator.initialize(1024);
				KeyPair keypair = generator.generateKeyPair();
				RSAPublicKey publickey = (RSAPublicKey)keypair.getPublic();
				RSAPrivateKey privatekey = (RSAPrivateKey)keypair.getPrivate();
				
				PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(privatekey.getEncoded());
				KeyFactory factory = KeyFactory.getInstance("RSA");
				RSAPrivateKey key = (RSAPrivateKey) factory.generatePrivate(spec);
				
				/** Sends the key modulus to the card */
				byte[] modulus = getBytes(key.getModulus());
				CommandAPDU capdu4 = new CommandAPDU(CLA_WALLET, INS_MODULUS, (byte) 0,(byte) 0, modulus);
				applet.transmit(capdu4);

				/** Sends the key exponent to the card */
				byte[] exponent = getBytes(key.getPrivateExponent());
				CommandAPDU capdu5 = new CommandAPDU(CLA_WALLET, INS_EXP, (byte) 0,(byte) 0, exponent);
				applet.transmit(capdu5);
				
				/** Saves the publickey to a file */
				FileOutputStream file2 = new FileOutputStream("Cards/"+toHexString(ID));
				file2.write(publickey.getEncoded());
				file2.close();		
				 
				/** Creates the blocking date of the card */
				Date date = new Date();
		        
				Calendar cal = Calendar.getInstance();
				cal.setTime(date);
				short month = (short) cal.get(Calendar.MONTH);
				short year = (short) cal.get(Calendar.YEAR);
				year = (short) (year + (short) 3);
				month = (short) (month + (short) 3);

				byte[] bl_date = new byte[4];
				
				bl_date[0] = (byte)((year & 0xFF00) >> 8);
				bl_date[1] = (byte)((year & 0x00FF) >> 0);
				bl_date[2] = (byte)((month & 0xFF00) >> 8);
				bl_date[3] = (byte)((month & 0x00FF) >> 0);
				
				/** Sends the blocking date to the card */
				CommandAPDU capdu6 = new CommandAPDU(CLA_WALLET, INS_BLDT,(byte) 0,(byte) 0,bl_date, BLOCKSIZE);
				applet.transmit(capdu6);
				
				display.append("The card is issued\n");
				issueButton.setEnabled(false);
			}
		} catch (Exception e) {
			throw new CardException(e.getMessage());
		}
	}
	
	byte[] getBytes(BigInteger big) {
		byte[] data = big.toByteArray();
		if (data[0] == 0) {
			byte[] tmp = data;
			data = new byte[tmp.length - 1];
			System.arraycopy(tmp, 1, data, 0, tmp.length - 1);
		}
		return data;
	}

	static String toHexString(byte[] in) {
		StringBuilder out = new StringBuilder(2*in.length);
		for(int i = 0; i < in.length; i++) {
			out.append(String.format("%02x ", (in[i] & 0xFF)));
		}
		return out.toString().toUpperCase();
	}
  
	public byte[] encrypt(byte[] data, SecretKey key) throws Exception {
	    ecipher = Cipher.getInstance("AES/ECB/NoPadding");
	    ecipher.init(Cipher.ENCRYPT_MODE, key);
	
		byte[] enc = ecipher.doFinal(data);
		return(enc);
	}

	/**
	 * Creates an instance of this class and puts it inside a frame.
	 * 
	 * @param arg
	 *            command line arguments.
	 */
	public static void main(String[] arg){
		JFrame frame = new JFrame(TITLE);
		frame.setSize(new Dimension(300,300));
		frame.setResizable(false);
		Container c = frame.getContentPane();
		personTerminal panel = new personTerminal(frame);
		c.add(panel);
		frame.addWindowListener(new CloseEventListener());
		frame.pack();
		frame.setVisible(true);
	}
}

/**
 * Class to close window.
 */
class CloseEventListener extends WindowAdapter {
	public void windowClosing(WindowEvent we) {
		System.exit(0);
	}
}
