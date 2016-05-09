package applet;

import javacard.framework.*;
import javacard.security.*;
import javacardx.crypto.*;

public class EpurseApplet extends Applet implements ISO7816
{

   private static final byte INS_ISSUE = (byte)0x40;
   private static final byte INS_KEY = (byte)0x41;
   private static final byte INS_ID = (byte)0x42;
   private static final byte INS_NONCE = (byte)0x43;
   private static final byte INS_SESSION = (byte)0x44;
   private static final byte INS_BALANCE = (byte)0xE0;
   private static final byte INS_CREDIT = (byte)0xD0;
   private static final byte INS_DEBIT = (byte)0x32;

   private static final byte STATE_INIT = 0;
   private static final byte STATE_KEY = 1;
   private static final byte STATE_ISSUED = 2;
   
   private static final byte STATE_NO_AUTH = 0;
   private static final byte STATE_AUTH = 1;
   
   AESKey secretkey;
   AESKey sessionkey;
   
   /** Maximum balance */
   private static final short max_balance = 0x2710;
      
   /** balance exceed the maximum limit error message*/
   final static short sw_exceed_maximum_balance = 0x84;
   
   /** balance becomes negative error message*/
   final static short sw_negative_balance = 0x85;
   
   /** amount error message */
   final static short sw_error_amount = 0x86;
   
   /** wrong terminal nonce */
   final static short SW_WRONG_NONCE = 0x87;
   
   /** The applet state (INIT, KEY or ISSUED). */
   byte state;

   /** The communication state (auth or not) */
   byte[] auth;
   
   /** The balance of the e-purse. */
   short balance;
   
   /** Temporary buffer in RAM. */
   byte[] tmp;
   byte[] nonce_t;
   byte[] nonce_c;
   byte[] nonce_c_id;
   byte[] nonce_combined;
   byte[] nonce_t_check;
   
   /** The id of the card. */
   byte[] id_c;
   
   /** Cipher for encryption and decryption. */
   Cipher cipher;

   public static void install(byte[] bArray, short bOffset, byte bLength){
      (new CalcApplet()).register(bArray, (short)(bOffset + 1), bArray[bOffset]);
   }

   public CalcApplet() {
      balance = 0;
      id_c = new byte[16];
      tmp = JCSystem.makeTransientByteArray((short)128,JCSystem.CLEAR_ON_RESET);
      auth = JCSystem.makeTransientByteArray((short)1,JCSystem.CLEAR_ON_RESET);
      nonce_t = JCSystem.makeTransientByteArray((short)16,JCSystem.CLEAR_ON_RESET);
      nonce_t_check = JCSystem.makeTransientByteArray((short)16,JCSystem.CLEAR_ON_RESET);
      nonce_c = JCSystem.makeTransientByteArray((short)16,JCSystem.CLEAR_ON_RESET);
      nonce_c_id = JCSystem.makeTransientByteArray((short)32,JCSystem.CLEAR_ON_RESET);
      nonce_combined = JCSystem.makeTransientByteArray((short)16,JCSystem.CLEAR_ON_RESET);
      secretkey = (AESKey)KeyBuilder.buildKey(KeyBuilder.TYPE_AES,KeyBuilder.LENGTH_AES_128,false);
      sessionkey = (AESKey)KeyBuilder.buildKey(KeyBuilder.TYPE_AES,KeyBuilder.LENGTH_AES_128,false);
      cipher = Cipher.getInstance(Cipher.ALG_AES_BLOCK_128_ECB_NOPAD,false);
      state = STATE_INIT;
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
               case INS_ISSUE:
                  state = STATE_KEY;
                  break;
               default:
                  ISOException.throwIt(SW_INS_NOT_SUPPORTED);
            }
            break;
         case STATE_KEY:
             switch(ins){
                case INS_KEY:
  				  	short lc = (short)(buf[OFFSET_LC] & 0x00FF);
  				  	readBuffer(apdu,tmp,(short) 0,lc);
  				  	secretkey.setKey(tmp, (short) 0);
  				  	break;
                case INS_ID:
  				  	short lc1 = (short)(buf[OFFSET_LC] & 0x00FF);
  				  	readBuffer(apdu,tmp,(short) 0,lc1);
  					Util.arrayCopy(tmp,(short) 0,id_c,(short) 0,(short) 16);
  				  	state = STATE_ISSUED;
                	break;
                default:
                   ISOException.throwIt(SW_INS_NOT_SUPPORTED);
             }
             break;
         case STATE_ISSUED:
        	switch(auth[0]){
        		case STATE_NO_AUTH:
        			switch(ins){
        				case INS_NONCE:
        				  nonce_auth(apdu);
  		                  break;
        				case INS_SESSION:
        					if (session_auth(apdu) == 1)
        						auth[0] = STATE_AUTH;
        					else
        						ISOException.throwIt(SW_WRONG_NONCE);
        				  break;
 		                default:
 		                  ISOException.throwIt(SW_INS_NOT_SUPPORTED);
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
		                  ISOException.throwIt(SW_INS_NOT_SUPPORTED);
	        		}
	        		break;
        		default:
	                  ISOException.throwIt(SW_INS_NOT_SUPPORTED);
        	}
        	break;
         default:
            ISOException.throwIt(SW_CONDITIONS_NOT_SATISFIED);
      }
   }
   
   private void nonce_auth(APDU apdu){
	      byte[] buffer = apdu.getBuffer();
		  short lc = (short)(buffer[OFFSET_LC] & 0x00FF);
		  readBuffer(apdu,nonce_t,(short) 0,lc);
		  
		  // generate random data------------//
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
   
   private short session_auth(APDU apdu){
	  byte[] buffer = apdu.getBuffer();
	  short lc = (short)(buffer[OFFSET_LC] & 0x00FF);
	  readBuffer(apdu,tmp,(short) 0,lc);
	  cipher.init(sessionkey,Cipher.MODE_DECRYPT);
	  cipher.doFinal(tmp,(short)0,(short) 16,nonce_t_check,(short)0);
	  
	  if (Util.arrayCompare(nonce_t,(short) 0, nonce_t_check, (short) 0, (short) 16) != 0)
		  return 0;
	  else{
		  cipher.init(sessionkey,Cipher.MODE_ENCRYPT);
		  cipher.doFinal(nonce_c,(short)0,(short) 16,buffer,(short)0);
		  
		  apdu.setOutgoing();
		  apdu.setOutgoingLength((short) 16);
		  apdu.sendBytes((short) 0,(short) 16);
		  
		  return 1;
	  }
   }
   
   private void credit(APDU apdu){
      byte[] buffer = apdu.getBuffer();
      short lc = (short)(buffer[OFFSET_LC] & 0x00FF);
      readBuffer(apdu,tmp,(short)0,lc);
      cipher.init(sessionkey,Cipher.MODE_DECRYPT);
      cipher.doFinal(tmp,(short)0,lc,buffer,(short)0);

      short amount = Util.getShort(buffer, (short) 0);
	     	  
 	  if ( (short) balance + amount > max_balance )
 		  ISOException.throwIt(sw_exceed_maximum_balance);
 	 
	  balance = (short) (balance + amount);
   }
   
   private void debit(APDU apdu){
	byte[] buffer = apdu.getBuffer();
	short lc = (short)(buffer[OFFSET_LC] & 0x00FF);
	
	readBuffer(apdu,tmp,(short)0,lc);
	cipher.init(sessionkey,Cipher.MODE_DECRYPT);
	cipher.doFinal(tmp,(short)0,lc,buffer,(short)0);
	
	short amount = Util.getShort(buffer, (short) 0);
	         	  	
	if ( (short) balance - amount < (short) 0 )
		ISOException.throwIt(sw_negative_balance);
	
	balance = (short) (balance - amount);
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
