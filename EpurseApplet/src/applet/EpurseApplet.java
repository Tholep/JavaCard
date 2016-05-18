package applet;

import javacard.framework.*;
import javacard.security.*;
import javacardx.crypto.*;

public class CalcApplet extends Applet implements ISO7816
{
	private static final byte INS_CHECK_ISSUE = (byte)0x40;
   
	private static final byte INS_KEY = (byte)0x41;
	private static final byte INS_ID = (byte)0x42;
	private static final byte INS_BLDT = (byte)0x43;
	private static final byte INS_MODULUS = (byte) 0x44;
	private static final byte INS_EXP = (byte) 0x45;
   
	private static final byte INS_NONCE = (byte)0x51;
	private static final byte INS_SESSION = (byte)0x52;
   
	private static final byte INS_CHECK_DATE = (byte) 0x53;
	private static final byte INS_BLOCK = (byte) 0x55;
      
	private static final byte INS_CREDIT = (byte)0x31;
	private static final byte INS_DEBIT = (byte)0x32;
	private static final byte INS_CREDIT_OK = (byte)0x33;
	private static final byte INS_DEBIT_OK = (byte)0x34;

	private static final byte INS_BALANCE = (byte)0x35;

	private static final short STATE_INIT = 0;
	private static final short STATE_ISSUED = 1;
   
	private static final byte STATE_NO_AUTH = 0;
	private static final byte STATE_AUTH = 1;
   
	AESKey secretkey;
	AESKey sessionkey;
	RSAPrivateKey privatekey;
   
	/** Error Message */
	private static final short sw_error_message = 0x84;
   
	/** Maximum balance */
	private static final short max_balance = 0x2710;
   
	/** The applet state (INIT, KEY or ISSUED). */
	short state;

	/** The communication state (auth or not) */
	byte[] auth;
   
	/** The balance of the e-purse. */
	short balance;
   
	/** Temporary buffers in RAM. */
	byte[] tmp;
	byte[] nonce_t;
	byte[] nonce_c;
	byte[] nonce_c_id;
	byte[] nonce_combined;
	byte[] nonce_c_check;
	byte[] tosign;
   
	/** The id of the card. */
	byte[] id_c;
   
	/** The blocking date of the card */
	byte[] block_date;
   
	/** Cipher for encryption and decryption. */
	Cipher cipher;
	Cipher cipher_sign;
	MessageDigest m_sha_1;
	Signature m_sign;

	public static void install(byte[] bArray, short bOffset, byte bLength){
		(new CalcApplet()).register(bArray, (short)(bOffset + 1), bArray[bOffset]);
	}

	public CalcApplet() {
		/** Variables and Arrays in EEPROM */
		balance = 0;
		state = STATE_INIT;
		id_c = new byte[16];
		block_date = new byte[4];
	   
		/** Arrays in RAM */
		tmp = JCSystem.makeTransientByteArray((short)256,JCSystem.CLEAR_ON_RESET);

		auth = JCSystem.makeTransientByteArray((short)1,JCSystem.CLEAR_ON_RESET);
		nonce_t = JCSystem.makeTransientByteArray((short)16,JCSystem.CLEAR_ON_RESET);
		nonce_c = JCSystem.makeTransientByteArray((short)16,JCSystem.CLEAR_ON_RESET);
		nonce_c_check = JCSystem.makeTransientByteArray((short)16,JCSystem.CLEAR_ON_RESET);
		nonce_c_id = JCSystem.makeTransientByteArray((short)32,JCSystem.CLEAR_ON_RESET);
		nonce_combined = JCSystem.makeTransientByteArray((short)16,JCSystem.CLEAR_ON_RESET);
	   
		tosign = JCSystem.makeTransientByteArray((short)24,JCSystem.CLEAR_ON_RESET);
	   
		/** Cryptographic keys */
		secretkey = (AESKey)KeyBuilder.buildKey(KeyBuilder.TYPE_AES,KeyBuilder.LENGTH_AES_128,false);
		sessionkey = (AESKey)KeyBuilder.buildKey(KeyBuilder.TYPE_AES,KeyBuilder.LENGTH_AES_128,false);
		privatekey = (RSAPrivateKey)KeyBuilder.buildKey(KeyBuilder.TYPE_RSA_PRIVATE,KeyBuilder.LENGTH_RSA_1024,false);

		/** The instance of Cryptographic, Hash and Signature algorithms used */
		cipher = Cipher.getInstance(Cipher.ALG_AES_BLOCK_128_ECB_NOPAD,false);
		m_sha_1 = MessageDigest.getInstance(MessageDigest.ALG_SHA, false);
		m_sign = Signature.getInstance(Signature.ALG_RSA_SHA_PKCS1, false);
	}

	public void process(APDU apdu){
		byte[] buf = apdu.getBuffer();

		byte ins = buf[OFFSET_INS];
            
		if (selectingApplet()) {
			return;
		}

		switch(state) {
			case STATE_INIT:
				switch(ins){
					case INS_CHECK_ISSUE:
						Util.setShort(buf, (short) 0, state);
	   					apdu.setOutgoing();
	   					apdu.setOutgoingLength((short) 2);
	   					apdu.sendBytes((short)0,(short) 2);
   						break;
					case INS_KEY:
	   					short lc = (short)(buf[OFFSET_LC] & 0x00FF);
   						readBuffer(apdu,tmp,(short) 0,lc);
						secretkey.setKey(tmp, (short) 0);
						break;
	   				case INS_ID:
	   					short lc1 = (short)(buf[OFFSET_LC] & 0x00FF);
	   					readBuffer(apdu,tmp,(short) 0,lc1);
	   					Util.arrayCopy(tmp,(short) 0,id_c,(short) 0,(short) 16);
   						break;
	   				case INS_MODULUS:
	   					lc = (short)(buf[OFFSET_LC] & 0x00FF);
	   					readBuffer(apdu,tmp,(short)0,lc);
   						privatekey.setModulus(tmp,(short)0,lc);
   						break;
	   				case INS_EXP:
	   					lc = (short)(buf[OFFSET_LC] & 0x00FF);
	   					readBuffer(apdu,tmp,(short)0,lc);
	   					privatekey.setExponent(tmp,(short)0,lc);
	   					break;
	   				case INS_BLDT:
	   					short lc2 = (short)(buf[OFFSET_LC] & 0x00FF);
	   					readBuffer(apdu,tmp,(short) 0,lc2);
	   					Util.arrayCopy(tmp,(short) 0,block_date,(short) 0,(short) 4);
	   					state = STATE_ISSUED;
	   					break;
	   				default:
	   					ISOException.throwIt(sw_error_message);
	   			}
	   			break;
			case STATE_ISSUED:
	   			switch(auth[0]){
	   				case STATE_NO_AUTH:
	   					switch(ins){
			   				case INS_CHECK_ISSUE:
			   					Util.setShort(buf, (short) 0, state);
			   					apdu.setOutgoing();
			   					apdu.setOutgoingLength((short) 2);
			   					apdu.sendBytes((short)0,(short) 2);
		   						break;
	   						case INS_NONCE:
	   							nonce_auth(apdu);
	   							break;
	   						case INS_SESSION:
	   							session_auth(apdu);
	   							break;
	   						case INS_BLOCK:
	   							/** changes the state to STATE_INIT
	   							 *  deletes the id and the secret key of the card
	   							 */
	   							state = STATE_INIT;
	   							id_c = null;
	   							secretkey = null;
	   							break;
	   						case INS_CHECK_DATE:
	   							check_date(apdu);
	   							/** if the check_date doesn't throw any exception then 
	   							 *  the current date is before the expiration date
	   							 *  and the card is finally authenticated
	   							 */
   								auth[0] = STATE_AUTH;
   								break;
	   						default:
	   							ISOException.throwIt(sw_error_message);
	   					}
	   					break;
	   				case STATE_AUTH:
	   					switch(ins) {
	   						case INS_CREDIT:
	   							credit(apdu);
	   							break;
	   						case INS_DEBIT:
	   							debit(apdu);
	   							break;
	   						case INS_BALANCE:
	   							fbalance(apdu);
	   							break;
	   						default:
	   							ISOException.throwIt(sw_error_message);
	   					}
	   					break;
	   				default:
	   					ISOException.throwIt(sw_error_message);
	   			}
	   			break;
	   		default:
	   			ISOException.throwIt(sw_error_message);
		}
	}

	private void check_date(APDU apdu){
		byte[] buffer = apdu.getBuffer();
		short lc = (short)(buffer[OFFSET_LC] & 0x00FF);
		readBuffer(apdu,tmp,(short) 0,lc);

		cipher.init(sessionkey,Cipher.MODE_DECRYPT);
		cipher.doFinal(tmp,(short)0,lc,buffer,(short)0);
	   
		short year_now = Util.getShort(buffer, (short) 0);
		short month_now = Util.getShort(buffer, (short) 2);

		short year_block = Util.getShort(block_date, (short) 0);
		short month_block = Util.getShort(block_date, (short) 2);
	   
		if(year_now > year_block){
			state = STATE_INIT;
			ISOException.throwIt(sw_error_message);
		}
		else if (year_now == year_block){
			if (month_now >= month_block){
				state = STATE_INIT;
				ISOException.throwIt(sw_error_message);
			}
		}
	}

   
	private void nonce_auth(APDU apdu){
		byte[] buffer = apdu.getBuffer();
		short lc = (short)(buffer[OFFSET_LC] & 0x00FF);
		readBuffer(apdu,nonce_t,(short) 0,lc);
	   
		/** Generates random data */
		RandomData rnd = RandomData.getInstance(RandomData.ALG_SECURE_RANDOM); 
		rnd.generateData(nonce_c, (short) 0, (short) 16);
		  
		Util.arrayCopy(nonce_c,(short) 0,nonce_c_id,(short) 0,(short) 16);
		Util.arrayCopy(id_c,(short) 0,nonce_c_id,(short)16, (short) 16);

		Util.arrayCopy(nonce_c_id, (short) 0, buffer, OFFSET_CDATA, (short) 32);
		apdu.setOutgoing();
		apdu.setOutgoingLength((short) 32);
		apdu.sendBytes(OFFSET_CDATA,(short) 32);
		  
		for (short i = 0; i < nonce_combined.length; i++) {
			nonce_combined[i] = (byte) (((short) nonce_t[i]) ^ ((short) nonce_c[i]));
		}

		cipher.init(secretkey,Cipher.MODE_ENCRYPT);
		cipher.doFinal(nonce_combined,(short)0,(short) 16,tmp,(short)0);
		sessionkey.setKey(tmp, (short) 0);		  
	}
   
	private void session_auth(APDU apdu){
		byte[] buffer = apdu.getBuffer();
		short lc = (short)(buffer[OFFSET_LC] & 0x00FF);
		readBuffer(apdu,tmp,(short) 0,lc);
		cipher.init(sessionkey,Cipher.MODE_DECRYPT);
		cipher.doFinal(tmp,(short)0,(short) 16,nonce_c_check,(short)0);
	  
		if (Util.arrayCompare(nonce_c,(short) 0, nonce_c_check, (short) 0, (short) 16) != 0)
			ISOException.throwIt(sw_error_message);
		else{
			cipher.init(sessionkey,Cipher.MODE_ENCRYPT);
			cipher.doFinal(nonce_t,(short)0,(short) 16,buffer,(short)0);
  
			apdu.setOutgoing();
			apdu.setOutgoingLength((short) 16);
			apdu.sendBytes((short) 0,(short) 16);
		}
	}
   
	private void credit(APDU apdu){
		byte[] buffer = apdu.getBuffer();
		short lc = (short)(buffer[OFFSET_LC] & 0x00FF);
	   
		/** Reads the buffer and add the bytes to tmp array */
		readBuffer(apdu,tmp,(short)0,lc);
	   
		/** Decrypts the data received and stores them to the buffer array
	    * buffer[0] is the instruction byte
	    * buffer[1] and buffer[2] have the amount of transaction
	    * buffer[3] is the counter
	    * buffer[4] and buffer[5] are the Terminal ID
	    * buffer[6] and buffer[7] are the Terminal counter
	    * the rest 20bytes are the hash value that the terminal has sent.
	    */	   
		cipher.init(sessionkey,Cipher.MODE_DECRYPT);
		cipher.doFinal(tmp,(short)0,lc,buffer,(short)0);
	   
		/** Creates the hash value of the 8 bytes and stores it to the tmp array*/
		m_sha_1.reset();
		m_sha_1.doFinal(buffer, (short) 0, (short) 8, tmp, (short) 0);
	   
		/** The card checks if the hash value from the terminal is equal 
	    * to the hash value that has been created from the 8 bytes 
	    * */
	   
		if (Util.arrayCompare(buffer,(short) 8, tmp, (short) 0, (short) 20) != 0){
			ISOException.throwIt(sw_error_message);
		}else{
			/** Gets the amount of the transaction: buffer[1] and buffer[2] bytes */
			short amount = Util.getShort(buffer, (short) 1);

			if ( (short) balance + amount > max_balance ) //to be checked for the position in the code! Possible time attack?
				ISOException.throwIt(sw_error_message);
		   
			/** Creates the response of the card
			 * tmp[0] = The instruction			 				(1 Byte )
			 * tmp[1] = The counter of this session increased by 1 (1 Byte )
			 * tmp[2] = The sign message. The card signs the INS_CREDIT,amount,Termina_id,Terminal_counter,Card_id	 (128 Bytes)
			 */
		   
			tmp[0] = INS_CREDIT_OK;
		   
			/** Increases the step of the process */
			tmp[1] = ++buffer[3];
		   
			/** Creates the array with the data that will be signed */
			/** adds the instruction byte */
			tosign[0] = INS_CREDIT;
			/** adds the amount */
			Util.setShort(tosign,(short) 1, amount);
			/** adds the Terminal ID */
			Util.arrayCopy(buffer,(short) 4,tosign,(short) 3, (short) 2);
			/** adds the Terminal counter */
			Util.arrayCopy(buffer,(short) 6,tosign,(short) 5, (short) 2);
			/** adds the Card ID */
			Util.arrayCopy(id_c,(short) 0,tosign,(short) 7, (short) 16);
		   		   
			/** Creates the signed message and adds it to the tmp array */
			m_sign.init(privatekey, Signature.MODE_SIGN);
			m_sign.sign(tosign, (short) 0, (short) 23, tmp, (byte) 2);
		   
			/** Hashes the Instruction, the counter and the signed message */
			m_sha_1.reset();
			m_sha_1.doFinal(tmp, (short) 0, (short) 130, tmp, (short) 130);
		   
			/** Encrypts the message */ 
			cipher.init(sessionkey,Cipher.MODE_ENCRYPT);
			short outLength = cipher.doFinal(tmp,(short)0,(short) 160,buffer,(short)0);
		   
			/** Protects the card by card tears */
			JCSystem.beginTransaction();
		   		   
			/** Increases the balance */
			balance = (short) (balance + amount);
		   
			/** Sends to the terminal the values for the log files */
			apdu.setOutgoing();
			apdu.setOutgoingLength(outLength);
			apdu.sendBytes((short)0,outLength);
		   
			JCSystem.commitTransaction();
		}
	}
   
	private void debit(APDU apdu){
		byte[] buffer = apdu.getBuffer();
		short lc = (short)(buffer[OFFSET_LC] & 0x00FF);
	   
		/** Reads the buffer and add the bytes to tmp array */
		readBuffer(apdu,tmp,(short)0,lc);
	   
		/** Decrypts the data received and store them to the buffer array
		    * buffer[0] is the instruction byte
		    * buffer[1] and buffer[2] have the amount of transaction
		    * buffer[3] is the counter
		    * buffer[4] and buffer[5] are the Terminal ID
		    * buffer[6] and buffer[7] are the Terminal counter
		    * the rest 20bytes are the hash value that the terminal has sent.
		    */	   
		cipher.init(sessionkey,Cipher.MODE_DECRYPT);
		cipher.doFinal(tmp,(short)0,lc,buffer,(short)0);
	   
		/** Creates the hash value of the 8 bytes and stores it to the tmp array*/
		m_sha_1.reset();
		m_sha_1.doFinal(buffer, (short) 0, (short) 8, tmp, (short) 0);
	   
		/** The card checks if the hash value from the terminal is equal 
		    * to the hash value that has been created from the 8 bytes 
		    * */
	   
		if (Util.arrayCompare(buffer,(short) 8, tmp, (short) 0, (short) 20) != 0){
			ISOException.throwIt(sw_error_message);
		}else{
			/** Gets the amount of the transaction: buffer[1] and buffer[2] bytes */
			short amount = Util.getShort(buffer, (short) 1);
		   
			if ( (short) balance - amount < (short) 0 ) //to be checked for the position in the code! Possible time attack?
				ISOException.throwIt(sw_error_message);
		   
			/** Creates the response of the card
			 * tmp[0] = The instruction			 				(1 Byte )
			 * tmp[1] = The counter of this session increased by 1 (1 Byte )
			 * tmp[2] = The sign message. The card signs the INS_CREDIT,amount,Termina_id,Terminal_counter,Card_id	 (128 Bytes)
			 */
		   
			tmp[0] = INS_DEBIT_OK;
		   
			/** Increases the counter of the session */
			tmp[1] = ++buffer[3];
		   
			/** Creates the array with the data that will be signed */
			/** adds the instruction byte */
			tosign[0] = INS_DEBIT; 
			/** adds the amount */
			Util.setShort(tosign,(short) 1, amount);
			/** adds the Terminal ID */
			Util.arrayCopy(buffer,(short) 4,tosign,(short) 3, (short) 2);
			/** adds the Terminal counter */
			Util.arrayCopy(buffer,(short) 6,tosign,(short) 5, (short) 2);
			/** adds the Card ID */
			Util.arrayCopy(id_c,(short) 0,tosign,(short)7, (short) 16);
		   
			/** Creates the signed message and adds it to the tmp array */
			m_sign.init(privatekey, Signature.MODE_SIGN);
			m_sign.sign(tosign, (short) 0, (short) 23, tmp, (byte) 2);
		   
			/** Hashes the Instruction, the counter and the signed message */
			m_sha_1.reset();
			m_sha_1.doFinal(tmp, (short) 0, (short) 130, tmp, (short) 130);
		   
			/** Encrypts the message */ 
			cipher.init(sessionkey,Cipher.MODE_ENCRYPT);
			short outLength = cipher.doFinal(tmp,(short)0,(short) 160,buffer,(short)0);
		   
			/** Protects the card by card tears */
			JCSystem.beginTransaction();
		   
			/** Increases the balance */
			balance = (short) (balance - amount);
		   		   
			/** Sends to the terminal the values for the log files */
			apdu.setOutgoing();
			apdu.setOutgoingLength(outLength);
			apdu.sendBytes((short)0,outLength);
		   
			JCSystem.commitTransaction();
		}
	}
   
	private void fbalance(APDU apdu){
		byte[] buffer = apdu.getBuffer();
		Util.setShort(tmp, (short) 0, balance);

		cipher.init(sessionkey,Cipher.MODE_ENCRYPT);
		short outLength = cipher.doFinal(tmp,(short)0,(short) 16,buffer,(short)0);
	     
		apdu.setOutgoing();
		apdu.setOutgoingLength(outLength);
		apdu.sendBytes((short)0,outLength);
	}
   
   
	private void readBuffer(APDU apdu, byte[] dest, short offset,short length) {
		byte[] buf = apdu.getBuffer();
		short readCount = apdu.setIncomingAndReceive();
		short i = 0;
		Util.arrayCopy(buf,OFFSET_CDATA,dest,offset,readCount);
		while ((short)(i + readCount) < length) {
			i += readCount;
			offset += readCount;
			readCount = (short)apdu.receiveBytes(OFFSET_CDATA);
			Util.arrayCopy(buf,OFFSET_CDATA,dest,offset,readCount);
		}
	}
}
