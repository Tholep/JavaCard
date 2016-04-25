package terminal;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
//import java.math.BigInteger;
import java.util.List;
import javax.swing.*;

//import java.security.*;
//import java.security.spec.*;
//import java.security.interfaces.*;s

import javax.smartcardio.*;


/**
 * Sample terminal for the E-Purse applet.
 */
public class EpurseTerminal extends JPanel implements ActionListener {

	static final String TITLE = "E-Purse Terminal";
	static final int DISPLAY_WIDTH = 30;
	static final int DISPLAY_HEIGHT = 15;
	static final int AMOUNT_WIDTH = 30;
	static final int AMOUNT_HEIGHT = 1;
    static final Font FONT = new Font("Monospaced", Font.BOLD, 24);


	static final String MSG_ERROR = "Error";
	static final String MSG_INVALID = "Invalid";

	static final byte[] APPLET_AID = { (byte) 0x3B, (byte) 0x29,
        (byte) 0x63, (byte) 0x61, (byte) 0x6C, (byte) 0x63, (byte) 0x01 };


	static final CommandAPDU SELECT_APDU = new CommandAPDU((byte) 0x00,
			(byte) 0xA4, (byte) 0x04, (byte) 0x00, APPLET_AID);

	private static final byte CLA_WALLET = (byte) 0xCC;
	private static final byte INS_ISSUE = (byte) 0x40;
	private static final byte INS_BALANCE = (byte) 0xE0;
	private static final byte INS_CREDIT = (byte) 0xD0;
	private static final byte INS_DEBIT = (byte) 0x32;

	private static final int STATE_INIT = 0;
	private static final int STATE_ISSUED = 1;

	/** GUI stuff. */
	JTextArea display, amountfield;
    JPanel keypad;

	/** GUI stuff. */
	JButton issueButton,creditButton,debitButton,balanceButton,clearButton;

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
		
		issueButton = new JButton("Issue");
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
		keypad.add(issueButton);
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
		issueButton.addActionListener(l);
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
				if (cs.isEmpty()) {
					display.setText("No terminals with a card found.");
					return;
				}

				while (true) {
					try {
						for (CardTerminal c : cs) {
							if (c.isCardPresent()) {
								try {
									Card card = c.connect("*");
									//System.out.println("Card: " + card);
									try {
										applet = card.getBasicChannel();
										ResponseAPDU resp = applet.transmit(SELECT_APDU);
										if (resp.getSW() != 0x9000) {
											throw new Exception("Select failed");
										}
																			
										// Wait for the card to be removed
										while (c.isCardPresent())
											;

										break;
									} catch (Exception e) {
										System.out.println("Card does not contain E-purse Applet!");
										sleep(2000);
										continue;
									}
								} catch (CardException e) {
									display.setText("Couldn't connect to card!");
									sleep(2000);
									continue;
								}
							} else {
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
				JButton button = (JButton) src;
				
				if (button.equals(issueButton)) {
					issue();
				} else if (button.equals(creditButton)) {
					String amount = amountfield.getText();
					amountfield.setText("0");
					credit(amount);
				} else if (button.equals(debitButton)) {
					String amount = amountfield.getText();
					amountfield.setText("0");
					debit(amount);
				} else if (button.equals(balanceButton)) {
					balance();
				} else if (button.equals(clearButton)) {
					amountfield.setText("0");
					creditButton.setEnabled(true);
					debitButton.setEnabled(true);
					display.setText("");
				} else{
		            char c = button.getText().charAt(0);
		            if (amountfield.getText().charAt(0) == '0'){
		            	amountfield.setText(String.valueOf(c));
		            }else{
		            	if (c == '.'){
		            		//String af = amountfield.getText();
		            		if (amountfield.getText().indexOf('.') == -1){
				            	amountfield.append(String.valueOf(c));
		            		}
		            	}else{
			            	amountfield.append(String.valueOf(c));
		            	}
		            }
				}
			}
		} catch (Exception e) {
			System.out.println("ERROR: " + e.getMessage());
		}
	}
	
/*	void check_issue() throws CardException{
		//try {
		CommandAPDU capdu = new CommandAPDU(CLA_WALLET, INS_CHECK_ISSUE,(byte) 0, (byte) 0,1);
		ResponseAPDU resp = applet.transmit(capdu);
		byte[] data = resp.getData();
		System.out.println(data);
		int value = ((data[0]&0xFF << 8)|(data[1] & 0xFF));
		System.out.println(value);
		if (value == STATE_ISSUED) {
			issueButton.setEnabled(false);
			amountfield.setText("0");
			state = STATE_ISSUED;
		} else{
			display.append("Please, issue the card");
		}
		//} catch (CardException e) {
		//	throw new Exception(e.getMessage());
		//}
	}*/
	
	/**
	 * Handles 'issue' button event.
	 * 
	 * @throws CardException
	 *             if something goes wrong.
	 */
	
	void issue() throws CardException {
		try {
			CommandAPDU capdu = new CommandAPDU(CLA_WALLET, INS_ISSUE,(byte) 0, (byte) 0);
			ResponseAPDU resp = applet.transmit(capdu);
			if (resp.getSW() != 0x9000) {
				display.append("The card has been already issued");
				issueButton.setEnabled(false);
				amountfield.setText("0");
			} else{
				display.append("The card is issued");
				issueButton.setEnabled(false);
			}
		} catch (Exception e) {
			throw new CardException(e.getMessage());
		}
	}

	/**
	 * Handles 'credit' button event.
	 */
	void credit(String amount) throws CardException {
		float amountfloat = Float.parseFloat(amount);
		byte[] data = {(byte) ((byte) amountfloat)};
		CommandAPDU capdu = new CommandAPDU(CLA_WALLET, INS_CREDIT,	(byte) 0x4,(byte) 0x0,data);
		ResponseAPDU rapdu = applet.transmit(capdu);
		
		/**wrong balance or wrong amount message */
		if (rapdu.getSW() == 0x84){
			display.setText("You cannot credit your card. \nYour balance cannot be more than 100E");
		} else if (rapdu.getSW() == 0x86){
			display.setText("Your amount has to be between 0 and 100E");
		}
	}

	/**
	 * Handles 'debit' button event.
	 */
	void debit(String amount) throws CardException {
		float amountfloat = Float.parseFloat(amount);
		byte[] data = {(byte) ((byte) amountfloat)};
		CommandAPDU capdu = new CommandAPDU(CLA_WALLET, INS_DEBIT,	(byte) 0x4,(byte) 0x0,data);
		ResponseAPDU rapdu = applet.transmit(capdu);
		
		/**negative balance message */
		if (rapdu.getSW() == 0x85){
			display.setText("You cannot do the purchase. \nThe amount is larger than your balance");
		} else if (rapdu.getSW() == 0x86){
			display.setText("Your amount has to be between 0 and 100E");
		}
	}

	/**
	 * Handles 'amount' button event.
	 */
	void balance() throws Exception {
		CommandAPDU capdu = new CommandAPDU(CLA_WALLET, INS_BALANCE,(byte) 0, (byte) 0,2);
		ResponseAPDU rapdu = applet.transmit(capdu);
		byte[] data = rapdu.getData();

		int value = ((data[0]&0xFF << 8)|(data[1] & 0xFF));
		System.out.println(value);
		float valuefloat = Float.intBitsToFloat(value);
		System.out.println(valuefloat);
		amountfield.setText(Float.toString(valuefloat));
		creditButton.setEnabled(false);
		debitButton.setEnabled(false);
	}

	String toHexString(byte[] in) {
		StringBuilder out = new StringBuilder(2*in.length);
		for(int i = 0; i < in.length; i++) {
			out.append(String.format("%02x ", (in[i] & 0xFF)));
		}
		return out.toString().toUpperCase();
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

/**
1.Display error messages
2.Float amount
3.Simple DES encryption of the amount
4.Generate a nonce
*/