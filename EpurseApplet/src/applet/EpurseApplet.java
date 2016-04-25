package applet;

import javacard.framework.*;
//import javacard.security.*;
//import javacardx.crypto.*;

public class EpurseApplet extends Applet implements ISO7816
{

   private static final byte INS_ISSUE = (byte)0x40;
   private static final byte INS_BALANCE = (byte)0xE0;
   private static final byte INS_CREDIT = (byte)0xD0;
   private static final byte INS_DEBIT = (byte)0x32;
   private static final byte INS_CHECK_ISSUE = (byte) 0x33;

   private static final byte STATE_INIT = 0;
   private static final byte STATE_ISSUED = 1;
   
   /** Maximum balance */
   private static final short max_balance = 100;
   
   /** Maximum amount */
   private static final byte max_amount = (byte) 0x64;
   
   /** Minimum amount */
   private static final byte min_amount = (byte) 0x0;
      
   /** balance exceed the maximum limit error message*/
   final static short sw_exceed_maximum_balance = 0x84;
   
   /** balance becomes negative error message*/
   final static short sw_negative_balance = 0x85;
   
   /** amount error message */
   final static short sw_error_amount = 0x86;
   
   /** The applet state (INIT or ISSUED). */
   byte state;

   /** The balance of the e-purse. */
   short balance;

   public static void install(byte[] bArray, short bOffset, byte bLength){
      (new EpurseApplet()).register(bArray, (short)(bOffset + 1), bArray[bOffset]);
   }

   public EpurseApplet() {
      balance = 0;
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
                  state = STATE_ISSUED;
                  break;
               default:
                  ISOException.throwIt(SW_INS_NOT_SUPPORTED);
            }
            break;
         case STATE_ISSUED:
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
            ISOException.throwIt(SW_CONDITIONS_NOT_SATISFIED);
      }
   }
   
   private void credit(APDU apdu){
      byte[] buffer = apdu.getBuffer();
      byte lc = buffer[OFFSET_LC];
      byte byteRead = (byte)(apdu.setIncomingAndReceive());
      
	  /** it is an error if the number of data bytes
	   read does not match the number in Lc byte*/
      if ( ( lc != 1 ) || (byteRead != 1) )
    	  	ISOException.throwIt(SW_WRONG_LENGTH);
      
 	  byte amount = buffer[OFFSET_CDATA];
 	  
 	  check_amount(amount);
 	  
 	  if ( (short) balance + amount > max_balance )
 		  ISOException.throwIt(sw_exceed_maximum_balance);
 	 
	  balance = (short) (balance + amount);
   }
   
   private void debit(APDU apdu){
	      byte[] buffer = apdu.getBuffer();
	      byte lc = buffer[OFFSET_LC];
	      byte byteRead = (byte)(apdu.setIncomingAndReceive());
	      
		  /** it is an error if the number of data bytes
		   read does not match the number in Lc byte*/
	      if ( ( lc != 1 ) || (byteRead != 1) )
	    	  	ISOException.throwIt(SW_WRONG_LENGTH);
	      
     	  byte amount = buffer[OFFSET_CDATA];
     	  
     	  check_amount(amount);
     	  
     	  if ( (short) balance - amount < (short) 0 )
     		    ISOException.throwIt(sw_negative_balance);
     	  
     	  balance = (short) (balance - amount);
   }
   
   private void fbalance(APDU apdu){
	      byte[] buffer = apdu.getBuffer();
          short le = apdu.setOutgoing(); /**Set JCRE to data-sent mode. It doesn't send any data.*/
          
          if (le < 2)
        	  ISOException.throwIt(SW_WRONG_LENGTH);
          apdu.setOutgoingLength((byte)2);

          Util.setShort(buffer, (short)0, balance);
          
          /**Send the data with sendByte command
          Two parameters: the offset into the APDU buffer where the data begins
          				  the length of the data*/
          
          apdu.sendBytes((short) 0, (short)2);
   }
   
   private void check_amount(byte amount){
	   if ((amount<=min_amount) || (amount>max_amount)){
		   ISOException.throwIt(sw_error_amount);
	   }
   }
}