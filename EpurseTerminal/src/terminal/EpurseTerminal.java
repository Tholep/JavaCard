package terminal;

import java.awt.*;
import java.awt.event.*;
import java.io.*;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Scanner;

import javax.swing.*;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

//import java.security.*;
//import java.security.spec.*;
//import java.security.interfaces.*;s
import java.security.SecureRandom;

import javax.smartcardio.*;


/**
 * Sample terminal for the E-Purse applet.
 */
public class EpurseTerminal extends JPanel implements ActionListener {

	static final int BLOCKSIZE = 128;
	
	static final String TITLE = "E-Purse Terminal";
	static final int DISPLAY_WIDTH = 30;
	static final int DISPLAY_HEIGHT = 15;
	static final int AMOUNT_WIDTH = 30;
	static final int AMOUNT_HEIGHT = 1;
    static final Font FONT = new Font("Monospaced", Font.BOLD, 24);


	//static final String MSG_ERROR = "Error";
	//static final String MSG_INVALID = "Invalid";

	static final byte[] APPLET_AID = { (byte) 0x3B, (byte) 0x29,
        (byte) 0x63, (byte) 0x61, (byte) 0x6C, (byte) 0x63, (byte) 0x01 };


	static final CommandAPDU SELECT_APDU = new CommandAPDU((byte) 0x00,
			(byte) 0xA4, (byte) 0x04, (byte) 0x00, APPLET_AID);

	private static final byte CLA_WALLET = (byte) 0xCC;
	private static final byte INS_NONCE = (byte) 0x44;
	private static final byte INS_SESSION = (byte) 0x45;
	private static final byte INS_CHECK_DATE = (byte) 0x46;
	private static final byte INS_CHECK_BLOCK = (byte) 0x47;
	private static final byte INS_BLOCK = (byte) 0x48;
	private static final byte INS_BALANCE = (byte) 0xE0;
	private static final byte INS_CREDIT = (byte) 0xD0;
	private static final byte INS_DEBIT = (byte) 0x32;

	private int counter = 0;
	
	//The master symmetric key of terminal
    private static byte[] secretkey = null ;
    static SecretKey key_m;
    
    //The session key established during authentication
    static SecretKey key_session;
    
	Cipher ecipher;
	Cipher dcipher;

	/** GUI stuff. */
	JTextArea display, amountfield;
	JPanel keypad;

	/** GUI stuff. */
	JButton creditButton,debitButton,balanceButton,clearButton;

		/** The card applet. */
	CardChannel applet;

	/**
	 * Constructs the terminal application.
	 */
	public EpurseTerminal(JFrame parent) {
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
		add(new JScrollPane(display), BorderLayout.SOUTH);
		
		creditButton = new JButton("Credit");
		debitButton = new JButton("Debit");
		balanceButton = new JButton("Balance");
		clearButton = new JButton("Clear");
		
		keypad = new JPanel(new GridLayout(4, 4));
		key("1");
		key("2");
		key("3");
		keypad.add(creditButton);
		key("4");
		key("5");
		key("6");
		keypad.add(debitButton);
		key("7");
		key("8");
		key("9");
		keypad.add(balanceButton);
		key(null);
		key("0");
		key(".");
		keypad.add(clearButton);
		
		add(keypad, BorderLayout.CENTER);
		
		amountfield = new JTextArea(AMOUNT_HEIGHT, AMOUNT_WIDTH);
		amountfield.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
		amountfield.setEditable(false);
		amountfield.setFont(FONT);
		amountfield.setBackground(Color.darkGray);
		amountfield.setForeground(Color.green);
		amountfield.setText("0");
		add(amountfield, BorderLayout.NORTH);
		
		parent.addWindowListener(new CloseEventListener());		
	}
	
    void key(String txt) {
        if (txt == null) {
            keypad.add(new JLabel());
        } else {
            JButton button = new JButton(txt);
            button.setPreferredSize(new Dimension(40, 60));
            button.addActionListener(this);
            keypad.add(button);
        }
    }
    
	/**
	 * Adds the action listener <code>l</code> to all buttons.
	 * 
	 * @param l
	 *            the action listener to add.
	 */
	public void addActionListener(ActionListener l) {
		creditButton.addActionListener(l);
		balanceButton.addActionListener(l);
		clearButton.addActionListener(l);
		debitButton.addActionListener(l);
	}

	class CardThread extends Thread {
		public void run() {
			try {
				TerminalFactory tf = TerminalFactory.getDefault();
				CardTerminals ct = tf.terminals();
				List<CardTerminal> cs = ct.list(CardTerminals.State.CARD_PRESENT);
				while (cs.isEmpty()) {
					display.setText("Insert your Card!");
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
										else{
											if (check_block() == 1){
												if (check_date() == 1){
													if (authenticate() == 1){
														display.setText("Authenticated");
														setEnabled(true);
													}else{
														throw new Exception("The card cannot be authenticated");
													}
												}else{
													setEnabled(false);
													throw new Exception("The card is not valid");
												}
											}else{
												setEnabled(false);
												throw new Exception("The card is not valid");												
											}
										}
										
										// Wait for the card to be removed
										while (c.isCardPresent())
											;

										break;
									} catch (Exception e) {
										display.setText(e.getMessage());
										sleep(1000);
										display.setText("");
										sleep(1000);
										continue;
									}
								} catch (CardException e) {
									display.setText("Couldn't connect to card!");
									sleep(2000);
									continue;
								}
							} else {
								setEnabled(false);
								display.setText("Insert your Card!");
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
				display.setText("ERROR: " + e.getMessage());
				e.printStackTrace();
			}			
		}
	}
	
	public void setEnabled(boolean b) {
		super.setEnabled(b);
		debitButton.setEnabled(b);
		creditButton.setEnabled(b);
		clearButton.setEnabled(b);
		balanceButton.setEnabled(b);
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
				JButton button = (JButton) src;
				
				if (button.equals(creditButton)) {
					String amount = amountfield.getText();
					amountfield.setText("0");
					credit(amount);
					counter = 0;
				} else if (button.equals(debitButton)) {
					String amount = amountfield.getText();
					amountfield.setText("0");
					debit(amount);
					counter = 0;
				} else if (button.equals(balanceButton)) {
					balance();
				} else if (button.equals(clearButton)) {
					amountfield.setText("0");
					creditButton.setEnabled(true);
					debitButton.setEnabled(true);
					display.setText("");
					counter = 0;
				} else{
		            char c = button.getText().charAt(0);
		            if (amountfield.getText().charAt(0) == '0'){
		            	amountfield.setText(String.valueOf(c));
		            }else{
		            	if (c == '.'){
		            		if (amountfield.getText().indexOf('.') == -1){
				            	amountfield.append(String.valueOf(c));
		            		}
		            	}else{
		            		if (amountfield.getText().indexOf('.') != -1){
		            			if (counter < 2){
		            				counter++;
		            				amountfield.append(String.valueOf(c));
		            			}
		            		}else{
		            			amountfield.append(String.valueOf(c));
		            		}
		            	}
		            }
				}
			}
		} catch (Exception e) {
			System.out.println("ERROR: " + e.getMessage());
		}
	}
	
	/**
	 * Check if the card is blocked
	 * @throws Exception 
	 */
	int check_block() throws Exception{
		byte[] data = new byte[16];
		boolean flag = false;
		
		CommandAPDU capdu = new CommandAPDU(CLA_WALLET, INS_CHECK_BLOCK,(byte) 0,(byte) 0,data, BLOCKSIZE);
		ResponseAPDU rapdu = applet.transmit(capdu);

		if (rapdu.getSW() != 0x9000){
			return 0;
		}else{
			
			data = rapdu.getData();
			
			/** a network file that contains the IDs of blocked_cards*/
			File file = new File("blocked_id.txt");
			
			Scanner scanner = new Scanner(file);
			while (scanner.hasNextLine()) {
				String lineFromFile = scanner.nextLine();
				if(lineFromFile.contains(toHexString(data))) {
					flag = true;
					break;
				}
			}
			
			if (flag == false){
				return 1;
			}else{
				CommandAPDU capdu2 = new CommandAPDU(CLA_WALLET, INS_BLOCK,(byte) 0,(byte) 0, BLOCKSIZE);
				applet.transmit(capdu2);
				return 0;
			}
		}
	}
	
	/**
	 * Check the expired date of the card
	 * @throws Exception 
	 */
	int check_date() throws Exception{
		Date date = new Date();
        
		Calendar cal = Calendar.getInstance();
		cal.setTime(date);
		short month = (short) cal.get(Calendar.MONTH);
		short year = (short) cal.get(Calendar.YEAR);

		month = (short) (month + (short) 1);
		
		byte[] bl_date = new byte[4];
		
		bl_date[0] = (byte)((year & 0xFF00) >> 8);
		bl_date[1] = (byte)((year & 0x00FF) >> 0);
		bl_date[2] = (byte)((month & 0xFF00) >> 8);
		bl_date[3] = (byte)((month & 0x00FF) >> 0);
		
		CommandAPDU capdu = new CommandAPDU(CLA_WALLET, INS_CHECK_DATE,(byte) 0,(byte) 0,bl_date, BLOCKSIZE);
		ResponseAPDU rapdu = applet.transmit(capdu);		
		
		if (rapdu.getSW() == 0x9000)
			return 1;
		else{
			return 0;
		}
	}
	
	/**
	 * Authentication between the terminal and the card
	 * @throws Exception 
	 */
	int authenticate() throws Exception{
		// Generate nonce of terminal
		SecureRandom random = new SecureRandom();
	    byte nonce_t[] = new byte[16];
	    random.nextBytes(nonce_t);
	    //System.out.println("Nonce of terminal: " + toHexString(nonce_t));
	    
	    //send nonce to the card
	    CommandAPDU capdu = new CommandAPDU(CLA_WALLET, INS_NONCE,(byte) 0, (byte) 0,nonce_t,BLOCKSIZE);
		ResponseAPDU rapdu = applet.transmit(capdu);
		
		//get the nonce and id of the card as one byte array
		byte[] incoming = rapdu.getData();

		//Split the byte array
	    byte nonce_c[] = Arrays.copyOf(incoming, 16);
	    //System.out.println("Nonce of the card: " + toHexString(nonce_c));
	    
	    byte id_c[] = Arrays.copyOfRange(incoming, 16,32);
	    //System.out.println("Id of the card: " + toHexString(id_c));
		
	    //retrieve the symmetric key of the card
	    byte[] k_id = encrypt(id_c,key_m);
	    //System.out.println("The k_id of the card: " + toHexString(k_id));
	    
	    SecretKey key_id = new SecretKeySpec(k_id, 0, k_id.length, "AES");
	    
	    //Create the session key
	    byte[] nonce_combined = new byte[16];
	    
  		for (int i = 0; i < nonce_combined.length; i++) {
    		nonce_combined[i] = (byte) (((int) nonce_t[i]) ^ ((int) nonce_c[i]));
  		}

  		//System.out.println("Nonce combined: " + toHexString(nonce_combined));
	    
	    byte[] sessionkey = encrypt(nonce_combined,key_id);
	    
  		//System.out.println("The Session Key: " + toHexString(sessionkey));
	    
	    key_session = new SecretKeySpec(sessionkey, 0, sessionkey.length, "AES");
	    
	    //encrypt the nonce of terminal with the session key and send it to the card
	    byte[] data = encrypt(nonce_t,key_session);
	    
	    CommandAPDU capdu2 = new CommandAPDU(CLA_WALLET, INS_SESSION,(byte) 0, (byte) 0,data,BLOCKSIZE);
		ResponseAPDU rapdu2 = applet.transmit(capdu2);
		
		//if the card didn't authenticate the terminal, the communication stops.
		if (rapdu2.getSW() != 0x9000){
			return 0;
		}
		else{
		
			//get the nonce of card encrypted with the session key
			byte[] encrypted = rapdu2.getData();
			byte[] data_2 = decrypt(encrypted,key_session);
	  		//System.out.println("The Nonce : " + toHexString(data_2));

			//if the data_2 and the nonce_c are same, then the authentication has been established.
			if (Arrays.equals(nonce_c,data_2) == true)
				return 1;
			else
				return 0;
		}
	    
	}
	
	
	/**
	 * Handles 'credit' button event.
	 * @throws Exception 
	 */
	void credit(String amount) throws Exception {
		int amountint = (int) (Float.parseFloat(amount)*100);
		if (check_amount(amountint) == 1){
			display.setText("Your amount has to be between 0 and 100E");
		}else{
			byte[] data = new byte[16];
			data[0] = (byte) ((amountint & 0xFF00) >> 8);
			data[1] = (byte) ((amountint & 0x00FF) >> 0);
	
			byte[] encrypted = encrypt(data,key_session);
			
			CommandAPDU capdu = new CommandAPDU(CLA_WALLET, INS_CREDIT,	(byte) 0,(byte) 0,encrypted, BLOCKSIZE);
			ResponseAPDU rapdu = applet.transmit(capdu);

			/**wrong balance or wrong amount message */
			if (rapdu.getSW() == 0x84){
				display.setText("The transaction is not possible.\n");
			}
		}
	}
	
	/**
	 * Handles 'debit' button event.
	 */
	void debit(String amount) throws Exception {
		int amountint = (int) (Float.parseFloat(amount)*100);
		if (check_amount(amountint) == 1){
			display.setText("Your amount has to be between 0 and 100E");
		}else{		
			byte[] data = new byte[16];
			data[0] = (byte) ((amountint & 0xFF00) >> 8);
			data[1] = (byte) ((amountint & 0x00FF) >> 0);	
			
			byte[] encrypted = encrypt(data,key_session); //change it with key_session
			
			CommandAPDU capdu = new CommandAPDU(CLA_WALLET, INS_DEBIT,	(byte) 0,(byte) 0,encrypted, BLOCKSIZE);
			ResponseAPDU rapdu = applet.transmit(capdu);
						
			/**negative balance message */
			if (rapdu.getSW() == 0x84){
				display.setText("The transaction is not possible.\n");
			}
		}
	}

	/**
	 * Handles 'amount' button event.
	 */
	void balance() throws Exception {
				 
		byte[] data = new byte[16];
		
		CommandAPDU capdu2 = new CommandAPDU(CLA_WALLET, INS_BALANCE,(byte) 0, (byte) 0,data,BLOCKSIZE);
		ResponseAPDU rapdu2 = applet.transmit(capdu2);

		data = rapdu2.getData();

	    byte[] decrypted = decrypt(data,key_session);
		
		short value = java.nio.ByteBuffer.wrap(decrypted).getShort();
		float valuefloat = (float) (value/100.0);

		amountfield.setText(String.valueOf(valuefloat));
		creditButton.setEnabled(false);
		debitButton.setEnabled(false);
	}

	static String toHexString(byte[] in) {
		StringBuilder out = new StringBuilder(2*in.length);
		for(int i = 0; i < in.length; i++) {
			out.append(String.format("%02x ", (in[i] & 0xFF)));
		}
		return out.toString().toUpperCase();
	}
	
	int check_amount(int amount){
		if ((amount == 0) || (amount > 10000))
			return 1;
		else
			return 0;
	}

	public byte[] encrypt(byte[] data, SecretKey key) throws Exception {
	    ecipher = Cipher.getInstance("AES/ECB/NoPadding");
	    ecipher.init(Cipher.ENCRYPT_MODE, key);
	
		byte[] enc = ecipher.doFinal(data);
		return(enc);
	}
	
    public byte[] decrypt(byte[] data, SecretKey key) throws Exception {
        dcipher = Cipher.getInstance("AES/ECB/NoPadding");
        dcipher.init(Cipher.DECRYPT_MODE, key);

	    byte[] dec = dcipher.doFinal(data);
	    return(dec);
	}
	  

	/**
	 * Creates an instance of this class and puts it inside a frame.
	 * 
	 * @param arg
	 *            command line arguments.
	 */
	public static void main(String[] arg) {
	    //reads the AES key from the file "key" and assigns it to the variable secretkey 
	    File file = new File("key");
	    secretkey = new byte[(int) file.length()];
	    try {
	       FileInputStream fileInputStream = new FileInputStream(file);
	       try {
			fileInputStream.read(secretkey);
		   } catch (IOException e) {
		        System.out.println("Error Reading The File.");
	        	e.printStackTrace();
		   }	
		}catch (FileNotFoundException e) {
          		System.out.println("File Not Found.");
          		e.printStackTrace();
        }
		
		key_m = new SecretKeySpec(secretkey, 0, secretkey.length, "AES");
        
		JFrame frame = new JFrame(TITLE);
		frame.setSize(new Dimension(300,300));
		frame.setResizable(false);
		Container c = frame.getContentPane();
		EpurseTerminal panel = new EpurseTerminal(frame);
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
