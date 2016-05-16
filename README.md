# JavaCard
JavaCard project for Hardware Security Course
=============================================

There are folders and txt files in the Terminals. Actually, they are a simple implementation of an online database with all the necessary values.

===================================================================================================================================================================

personTerminal
-----------------------
  Personalizes the card. 
  Reads the master_key from the "key" file. 
  Generates a 16byte ID  and saves every ID generated to the "cards_id.txt" file.
  Encrypts it with the master_key to generate the key_id of the card.
  Creates a signature pair. The terminal sends the private key to the card and stores the publickey to the folder Cards and uses the ID of the card as the filename.
  Also, the personTerminal sends to the card the expired date (2 months and 3 years after from the current date).

====================================================================================================================================================================

EpurseTerminal (reloadTerminal)
-------------------------------
  This terminal credits the card and gets the balance of the card
  Reads the information of terminal:
	1. The terminal id from the "terminal_id.txt" file
	2. The terminal counter from the "terminal_counter.txt" file
  Reads the master key from the "key" file.
  Creates the GUI of the terminal. The user can credit the card one time per session; however, can see his balance as many times he wants per session.
  When the user inserts the card to the terminal, the terminal:
	1. Checks if the card is in blocking list ("blocked_id.txt")
	2. Checks if the card has been expired
	3. If everything is ok with the blocking procedure the terminal authenticates the card. The terminal implements the protocol step by step.
  After the authentication the user can debit the card or check his balance.
  In credit process:
	1. The terminal checks if the amount given by user is between 0-100€.
	2. Implements the protocol.
	3. If the maximum balance has been reached then increase the terminal_counter and display an error message. The terminal does not save any information to the logs.
	4. If the maximum balance has not been reached then the terminal updates the log file ("terminal_logs.txt"). The log file contains:
		1) Terminal's counter
		2) Terminal's id
		3) Card's id
		4) The HEX value of the command
		5) The integer value of amount
		6) The signed value that card has sent to the terminal
		7) A "boolean" value which represents if everything went fine (1) or not (0)
  In balance process:
	The terminal asks from the card to return the balance.
	The card sends the balance encrypted.
	The terminal decrypts it and displays it to the amountfield of the GUI.

====================================================================================================================================================================

PoSTerminal (debitTerminal)
---------------------------
The same procedure as the EpurseTerminal, except of balance procedure.
Error message for insufficient balance: The terminal increases the terminal_counter and does not save any information to the logs.

====================================================================================================================================================================

EpurseApplet
---------------------------
The applet has 3 states:
	1. STATE_INIT: if the card is not initiated or the card has been blocked / expired
	2. STATE_KEY: After the initiation phase the personTerminal has to send the key_id, the privatekey (for signature), the id of the card, and the expired date
	3. STATE_ISSUED: The card has already been issued. The user can uses his card.

In STATE_ISSUED there are 2 different states:
	1. STATE_NO_AUTH: the card and the terminal have to mutual authenticate.
		1) The terminal checks if the card is blocked
		2) The terminal sends the current date and the cards compares it with the expired date
		3) If everything is ok, the terminal and the card implements the authenetication protocol. Exchange nonces and create the session_key.
	2. STATE_AUTH: the card has authenticated the terminal. The user can debit, credit or check his balance.
		In credit and debit functions the card creates a signed value and sends it back to the terminal for logging purposes.

