package posterminal;

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

import java.security.*;
import java.security.spec.X509EncodedKeySpec;

import javax.smartcardio.*;


/**
 * Sample terminal for the E-Purse applet.
 */
public class PoSTerminal extends JPanel implements ActionListener {

	static final int BLOCKSIZE = 256;
	
	static final String TITLE = "E-Purse Terminal";
	static final int DISPLAY_WIDTH = 30;
	static final int DISPLAY_HEIGHT = 15;
	static final int AMOUNT_WIDTH = 30;
	static final int AMOUNT_HEIGHT = 1;
    static final Font FONT = new Font("Monospaced", Font.BOLD, 24);

	static final String MSG_ERROR = "Aborted\nPlease remove your card";
	static final String MSG_APPROVED = "Approved\nThank you!\nPlease remove your card";
    
	static final byte[] APPLET_AID = { (byte) 0x3B, (byte) 0x29,(byte) 0x63, (byte) 0x61, (byte) 0x6C, (byte) 0x63, (byte) 0x01 };
	static final CommandAPDU SELECT_APDU = new CommandAPDU((byte) 0x00, (byte) 0xA4, (byte) 0x04, (byte) 0x00, APPLET_AID);
	
	private static final byte CLA_WALLET = (byte) 0xCC;
	
	private static final byte INS_NONCE = (byte) 0x44;
	private static final byte INS_SESSION = (byte) 0x45;
	
	private static final byte INS_CHECK_DATE = (byte) 0x46;
	private static final byte INS_CHECK_BLOCK = (byte) 0x47;
	private static final byte INS_BLOCK = (byte) 0x48;
		
	private static final byte INS_DEBIT = (byte)0x32;
	private static final byte INS_DEBIT_OK = (byte)0x34;

	/** Counter of decimal digits in the amount */
	private int counter = 0;
	
	/** The position of the transaction */
	private byte counter_trans = (byte) 0x00;
	
	private int terminal_counter;
	
	/**The master symmetric key of terminal */
    private static byte[] secretkey = null ;
    static SecretKey key_m;
    
    /** The id of the card */
    byte[] id_c = new byte[16];
    
    /** The session key established during authentication*/
    static SecretKey key_session;
    
    /** Terminal Information */
    String t_id;
    String t_counter;
    
    /** Cipher for encyrption */
	Cipher ecipher;
	
	/** Cipher for decryption */
	Cipher dcipher;

	/** GUI stuff. */
	JTextArea display, amountfield;
	JPanel keypad;
	JButton debitButton,clearButton;

	/** The card applet. */
	CardChannel applet;

	/**
	 * Constructs the terminal application.
	 */
	public PoSTerminal(JFrame parent) {
		readterminalinfo();
		readkey();
		buildGUI(parent);
		setEnabled(false);
		addActionListener(this);
		(new CardThread()).start();
	}
	
	void readterminalinfo(){
		File file = new File("terminal_id.txt");
		File file2 = new File("terminal_counter.txt");
		
		try {
			Scanner scanner = new Scanner(file);
			t_id = scanner.nextLine();
		} catch (FileNotFoundException e) {
      		System.out.println("File Not Found.");
			e.printStackTrace();
		}
		
		try {
			Scanner scanner = new Scanner(file2);
			t_counter = scanner.nextLine();
			terminal_counter = (int) Integer.parseInt(t_counter);
		} catch (FileNotFoundException e) {
      		System.out.println("File Not Found.");
			e.printStackTrace();
		}
	}
	
	void readkey(){
	    /** Reads the AES key from the file "key" and assigns it to the variable secretkey */ 
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
		
		/** Creates the master key */
		key_m = new SecretKeySpec(secretkey, 0, secretkey.length, "AES");
	}

	/**
	 * Builds the GUI.
	 */
	void buildGUI(JFrame parent) {
		setLayout(new BorderLayout());
		display = new JTextArea(DISPLAY_HEIGHT, DISPLAY_WIDTH);
		display.setEditable(false);
		add(new JScrollPane(display), BorderLayout.SOUTH);
		
		debitButton = new JButton("Pay");
		clearButton = new JButton("Clear");
		
		keypad = new JPanel(new GridLayout(4, 4));
		key("1");
		key("2");
		key("3");
		keypad.add(debitButton);
		key("4");
		key("5");
		key("6");
		keypad.add(clearButton);
		key("7");
		key("8");
		key("9");
		key(null);
		key(null);
		key("0");
		key(".");
		key(null);
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
										
										/** Wait for the card to be removed */
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
		clearButton.setEnabled(b);
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
				
				if (button.equals(debitButton)){
					String amount = amountfield.getText();
					amountfield.setText("0");
					debit(amount);
					counter = 0;
					setEnabled(false);
				} else if (button.equals(clearButton)){
					amountfield.setText("0");
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
			System.out.println(MSG_ERROR);
		}
	}
	
	/**
	 * Checks if the card is blocked
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
			
			/** Returns the id of the card */
			data = rapdu.getData();
			
			/** A network file that contains the IDs of blocked_cards*/
			File file = new File("blocked_id.txt");
			
			Scanner scanner = new Scanner(file);
			while (scanner.hasNextLine()) {
				String lineFromFile = scanner.nextLine();

				/** Compares the id of the card with the contents of the file */
				if(lineFromFile.contains(toHexString(data))) {
					flag = true;
					break;
				}
			}
			
			/** If the ID of the card is not in the block_id file then
			 *  	the application continues normally
			 *  else
			 *  	the terminal sends the INS_BLOCK instruction to the card
			 *  	and blocks the card
			 */
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
	 * Checks the expired date of the card
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
		
		/** Sends to the card the current day and month */
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
		/** Generates nonce of terminal */
		SecureRandom random = new SecureRandom();
	    byte nonce_t[] = new byte[16];
	    random.nextBytes(nonce_t);
	    
	    /** Sends nonce to the card */
	    CommandAPDU capdu = new CommandAPDU(CLA_WALLET, INS_NONCE,(byte) 0, (byte) 0,nonce_t,BLOCKSIZE);
		ResponseAPDU rapdu = applet.transmit(capdu);
		
		/** Gets the nonce and id of the card as one byte array */
		byte[] incoming = rapdu.getData();

		/** Splits the byte array to nonce of the card and id of the card*/
	    byte nonce_c[] = Arrays.copyOf(incoming, 16);   
	    id_c = Arrays.copyOfRange(incoming, 16,32);
		
	    /** Retrieves the symmetric key of the card */
	    byte[] k_id = encrypt(id_c,key_m);	    
	    SecretKey key_id = new SecretKeySpec(k_id, 0, k_id.length, "AES");
	    
	    /** XOR of nonces */
	    byte[] nonce_combined = new byte[16];
  		for (int i = 0; i < nonce_combined.length; i++) {
    		nonce_combined[i] = (byte) (((int) nonce_t[i]) ^ ((int) nonce_c[i]));
  		}

	    /** Creates the session key */
	    byte[] sessionkey = encrypt(nonce_combined,key_id);	    
	    key_session = new SecretKeySpec(sessionkey, 0, sessionkey.length, "AES");
	    
	    /** Encrypts the nonce of card with the session key and send it to the card */
	    byte[] data = encrypt(nonce_c,key_session);
	    
	    CommandAPDU capdu2 = new CommandAPDU(CLA_WALLET, INS_SESSION,(byte) 0, (byte) 0,data,BLOCKSIZE);
		ResponseAPDU rapdu2 = applet.transmit(capdu2);
		
		/** If the card didn't authenticate the terminal, the communication stops. */
		if (rapdu2.getSW() != 0x9000){
			return 0;
		}
		else{
		
			/** Gets the nonce of terminal encrypted with the session key */
			byte[] encrypted = rapdu2.getData();
			byte[] data_2 = decrypt(encrypted,key_session);

			/** If the data_2 and the nonce_t are same, then the authentication has been established. */
			if (Arrays.equals(nonce_t,data_2) == true)
				return 1;
			else
				return 0;
		}
	    
	}
	
	/**
	 * Handles 'debit' button event.
	 * @throws Exception 
	 */
	void debit(String amount) throws Exception {
		/** Converts the string amount to integer
		 * 	Multiples the amount with 100 
		 * 	for the case of decimals in amount
		 */
		int amountint = (int) (Float.parseFloat(amount)*100);
		
		/** Checks if the given amount is between 0 and 100 */
		if (check_amount(amountint) == 1){
			display.setText("Your amount has to be between 0 and 100\u20ac");
		}else{
			/** Creates the byte array data with the data to be hashed 
			 * data[0] = The instruction			 (1 Byte )
			 * data[1] = The amount of transaction	 (2 Bytes)
			 * data[3] = The counter of this session (1 Byte )
			 * data[4] = The terminal id			 (2 Bytes)
			 * data[6] = The terminal counter		 (2 Bytes)
			 */
			byte[] data = new byte[8];
			data[0] = INS_DEBIT;
			data[1] = (byte) ((amountint & 0xFF00) >> 8);
			data[2] = (byte) ((amountint & 0x00FF) >> 0);
			data[3] = counter_trans;
			
			int terminal_id = (int) Integer.parseInt(t_id);
			
			data[4] = (byte) ((terminal_id &0xFF00) >> 8);
			data[5] = (byte) ((terminal_id &0x00FF) >> 0);
			
			data[6] = (byte) ((terminal_counter &0xFF00) >> 8);
			data[7] = (byte) ((terminal_counter &0x00FF) >> 0);

			/** Increases the counter of the transaction */
			counter_trans++;
			
			/** Hashes the data with SHA-1 algorithm, which produce 20Bytes hash value*/
			MessageDigest md = MessageDigest.getInstance("SHA-1");
			md.update(data);
			byte[] hashed = md.digest();

			/** Creates the final byte array which will be encrypted = data + hash(data) */
			byte[] toencrypt = new byte[32];
			System.arraycopy(data,0,toencrypt,0,data.length);
			System.arraycopy(hashed,0,toencrypt,data.length,hashed.length);			
			
			/** Encrypts the toencrypt byte array */
			byte[] encrypted = encrypt(toencrypt,key_session);
			
			/** Sends the encrypted data to the card with INS_DEBIT instruction */
			CommandAPDU capdu = new CommandAPDU(CLA_WALLET, INS_DEBIT,	(byte) 0,(byte) 0,encrypted, BLOCKSIZE);
			ResponseAPDU rapdu = applet.transmit(capdu);
			

			if (rapdu.getSW() != 0x9000){
				/** Error message for insufficient balance */
				display.setText(MSG_ERROR);
			}else{
				/** Call the logs function and writes to the log file */
				logs(INS_DEBIT,rapdu,md,amount);
			}
			
			/** Increase the terminal counter */
			terminal_counter++;
			t_counter = Integer.toString(terminal_counter);
			File file2 = new File("terminal_counter.txt");
			FileWriter fw = new FileWriter(file2.getAbsoluteFile(), false);
			BufferedWriter bw = new BufferedWriter(fw);
			bw.write(t_counter);
			bw.close();	
		}
	}
	
	/** Creates the log file
	 * 
	 * @param command
	 * @param rapdu
	 * @param md
	 * @param amount
	 * @throws Exception
	 */
	
	void logs(byte command, ResponseAPDU rapdu, MessageDigest md,String amount) throws Exception{
		/** Gets the data sent by the card
		 * 	and 
		 *	Decrypts them
		 */
		byte[] card_response = rapdu.getData();
		byte[] decrypted = decrypt(card_response,key_session);

		/** Creates a new array with the instruction value and the counter sent by the card */
		byte[] data2 = new byte[130];
		
		/** the instruction */
		data2[0] = decrypted[0];
		
		/** the counter */
		data2[1] = decrypted[1];
		
		/** Gets the signed data sent by the card */
		byte[] signedvalue = new byte[128];
		System.arraycopy(decrypted,2,data2,2,128);
		System.arraycopy(decrypted,2,signedvalue,0,128);

		/** Hashes the instruction, the counter and the signed data */
		md.update(data2);
		byte[] hashed2 = md.digest();

		/** Gets the hash value sent by the card */
		byte[] hashed_card = new byte[20];
		System.arraycopy(decrypted,130,hashed_card,0,20);

		/** Creates/opens the log file */
		File file1 = new File("terminal_logs.txt");

		if (!file1.exists()) {
			file1.createNewFile();
		}

		FileWriter fw = new FileWriter(file1.getAbsoluteFile(), true);
		BufferedWriter bw = new BufferedWriter(fw);
		
		/** 
		 * 
		 *  If the hash values match then
		 *  	store the data to the log file and the value 1, which means that everything did properly
		 *  else
		 *  	store the data to the log file and the value 0, which means that something went wrong   
		 * 
		 */
		
		if ((java.util.Arrays.equals(hashed2,hashed_card) == true) && (data2[1] == counter_trans)){
			bw.write(t_counter + " | " + t_id + " | " + toHexString(id_c) + "| 0x" + Integer.toHexString(command) + " | " + amount + " | " + toHexString(signedvalue) + "| " + "1");
			bw.newLine();
			bw.close();

			/** Increases the counter of the session */
			counter_trans = (byte)(counter_trans + (byte) 1); 
			display.setText(MSG_APPROVED);
		}
		else{
			bw.write(t_counter + " | " + t_id + " | " + toHexString(id_c) + "| " + Integer.toHexString(command) + " | " + amount + " | " + toHexString(signedvalue) + "| " + "0");
			bw.newLine();
			bw.close();
			display.setText(MSG_ERROR);
		}
	}

	static String toHexString(byte[] in) {
		StringBuilder out = new StringBuilder(2*in.length);
		for(int i = 0; i < in.length; i++) {
			out.append(String.format("%02x ", (in[i] & 0xFF)));
		}
		return out.toString().toUpperCase();
	}
	
	/** Checks the given amount */
	int check_amount(int amount){
		if ((amount == 0) || (amount > 10000))
			return 1;
		else
			return 0;
	}

	/** Encryption function */
	public byte[] encrypt(byte[] data, SecretKey key) throws Exception {
	    ecipher = Cipher.getInstance("AES/ECB/NoPadding");
	    ecipher.init(Cipher.ENCRYPT_MODE, key);
	
		byte[] enc = ecipher.doFinal(data);
		return(enc);
	}
	
	/** Decryption function */
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
		JFrame frame = new JFrame(TITLE);
		frame.setSize(new Dimension(300,300));
		frame.setResizable(false);
		Container c = frame.getContentPane();
		PoSTerminal panel = new PoSTerminal(frame);
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