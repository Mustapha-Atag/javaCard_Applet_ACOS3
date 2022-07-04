/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package javaCard_Applet_ACOS3;

import javacard.framework.*;

/**
 *
 * @author Mustapha
 */
public class javaCard_Applet_ACOS3 extends Applet {
    final private static byte CLA = (byte) 0x80;
    
    // INS
    final private static byte INS_SELECT_FILE = (byte) 0xA4;
    final private static byte INS_SUBMIT_CODE = (byte) 0x20;
    final private static byte INS_RESET_APPLET = (byte) 0x30;
    final private static byte INS_WRITE_RECORD = (byte) 0xD2;
    final private static byte INS_READ_RECORD = (byte) 0xB2;
    final private static byte INS_CHANGE_PIN = (byte) 0x24;
    
    // SW
    final private static short SW_WRONG_CODE = 0x63C0;
    final private static short SW_CODE_BLOQUE = 0x6983;
    
    // FILE SYSTEM
    final private static byte MAX_N_OF_FILE = (byte) 64;
    
    private byte FF02[] = {(byte) 0x00}; // {N_OF_FILE}
    private byte FF03[] = {(byte) 0x08, 0x08, 0x08, 0x08, 0x00, 0x00, 0x00, 0x00}; // {IC_CODE, PIN_CODE}
    private byte FF04[] = new byte[MAX_N_OF_FILE*6]; // 64 is max number of files we can create
    private Object UserFile[] = new Object[255];
    
    final private byte FF02_RECORD_LEN = 1;
    final private byte FF03_RECORD_LEN = 4;
    final private byte FF04_RECORD_LEN = 6;
    
    final private byte FF02_NUMBER_OF_RECORDS = 1;
    final private byte FF03_NUMBER_OF_RECORDS = 2;
    private short FF04_NUMBER_OF_RECORDS = 0;
    
    private short selectedFile;
    
    // GLOBAL VARIABLES
    // IC CODE
    final private byte IC_TRY_LIMIT = 5;
    final private byte IC_LENGHT = 4;
    OwnerPIN IC;
    boolean ICBloque = false;
    
    // PIN
    final private byte PIN_TRY_LIMIT = 3;
    final private byte PIN_LENGHT = 4;
    OwnerPIN PIN;
    boolean pinBloque = false;
    
    
    /**
     * Installs this applet.
     * 
     * @param bArray
     *            the array containing installation parameters
     * @param bOffset
     *            the starting offset in bArray
     * @param bLength
     *            the length in bytes of the parameter data in bArray
     */
    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new javaCard_Applet_ACOS3();
    }

    /**
     * Only this class's install method should create the applet object.
     */
    protected javaCard_Applet_ACOS3() {
        IC = new OwnerPIN(IC_TRY_LIMIT, IC_LENGHT);
        IC.update(FF03, (short) 0, (byte) 4);
        PIN = new OwnerPIN(PIN_TRY_LIMIT, PIN_LENGHT);
        PIN.update(FF03, (short) 4, (byte) 4);
        register();
    }

    /**
     * Processes an incoming APDU.
     * 
     * @see APDU
     * @param apdu
     *            the incoming APDU
     */
    public void process(APDU apdu) {
        //Insert your code here
        byte buffer[] = apdu.getBuffer();
        
        if (selectingApplet()) return;
        
        if (buffer[ISO7816.OFFSET_CLA] != CLA)
            ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);
        
        switch(buffer[ISO7816.OFFSET_INS]) {
            case INS_SELECT_FILE:
                selectFile(apdu);
                break;
            case INS_SUBMIT_CODE:
                submitCode(apdu);
                break;
            case INS_RESET_APPLET:
                //resetApplet(apdu);
                break;
            case INS_WRITE_RECORD:
                writeRecord(apdu);
                break;
            case INS_READ_RECORD:
                readRecord(apdu);
                break;
            case INS_CHANGE_PIN:
                //changeCode(apdu);
                break;
            default:
                ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
        
    }
    
    void selectFile(APDU apdu) {
        byte[] buffer = apdu.getBuffer();
        short fid = Util.getShort(buffer, ISO7816.OFFSET_CDATA);
        switch (fid) {
            case (short) 0xFF02:
            case (short) 0xFF03:
            case (short) 0xFF04:
                selectedFile = fid;
                break;
            default:
                short userFileId;
                for (int i = 0; i < FF02[0]; i++) {
                    userFileId = Util.getShort(FF04, (short) (6*i + 4));
                    if (userFileId == fid) {
                        selectedFile = fid;
                        return;
                    }
                }
                
                ISOException.throwIt(ISO7816.SW_FILE_NOT_FOUND);
        }   
    }
    
    void writeRecord(APDU apdu) {
        byte[] buffer = apdu.getBuffer();
        if (selectedFile == (short) 0xFF02) {
            //if (!IC.isValidated()) ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
            if (buffer[ISO7816.OFFSET_P1] >= FF02_NUMBER_OF_RECORDS) ISOException.throwIt(ISO7816.SW_RECORD_NOT_FOUND);
            if (buffer[ISO7816.OFFSET_LC] != FF02_RECORD_LEN) ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
            
            FF02[0] = buffer[ISO7816.OFFSET_CDATA]; 
            FF04_NUMBER_OF_RECORDS = buffer[ISO7816.OFFSET_CDATA]; // N_OF_FILE
            
        } else if (selectedFile == (short) 0xFF03) {
            if (buffer[ISO7816.OFFSET_P1] >= FF03_NUMBER_OF_RECORDS) ISOException.throwIt(ISO7816.SW_RECORD_NOT_FOUND);
            if (buffer[ISO7816.OFFSET_LC] != FF03_RECORD_LEN) ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
            
            byte record_indx = buffer[ISO7816.OFFSET_P1];
            for (byte i = 0; i < buffer[ISO7816.OFFSET_LC]; i++) {
                FF03[(short) ((FF03_RECORD_LEN*record_indx) + i)] = buffer[(short) (ISO7816.OFFSET_CDATA+i)];
            }
            
        } else if (selectedFile == (short) 0xFF04) {
            if (buffer[ISO7816.OFFSET_P1] >= FF04_NUMBER_OF_RECORDS) ISOException.throwIt(ISO7816.SW_RECORD_NOT_FOUND);
            if (buffer[ISO7816.OFFSET_LC] != FF04_RECORD_LEN) ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
            
            byte record_indx = buffer[ISO7816.OFFSET_P1];
            for (byte i = 0; i < buffer[ISO7816.OFFSET_LC]; i++) {
                FF04[(short) ((FF04_RECORD_LEN*record_indx) + i)] = buffer[(short) ISO7816.OFFSET_CDATA+i];
            }
            
            UserFile[record_indx] = new byte[buffer[ISO7816.OFFSET_CDATA]*buffer[ISO7816.OFFSET_CDATA+1]];
            
        } else {
            short userFileId;
            for (byte i = 0; i < FF02[0]; i++) {
                userFileId = Util.getShort(FF04, (short) (FF04_RECORD_LEN*i + 4));
                if (userFileId == selectedFile) {
                    if (buffer[ISO7816.OFFSET_P1] >= FF04[(short) (FF04_RECORD_LEN*i+1)]) ISOException.throwIt(ISO7816.SW_RECORD_NOT_FOUND);
                    if (buffer[ISO7816.OFFSET_LC] != FF04[(short) (FF04_RECORD_LEN*i)]) ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
                    
                    byte record_indx = buffer[ISO7816.OFFSET_P1];
                    byte file_record_len = FF04[(short) (FF04_RECORD_LEN*i)];
                    for (byte j = 0; j < buffer[ISO7816.OFFSET_LC]; j++) {
                        ((byte[]) UserFile[i])[(short) (file_record_len*record_indx + j)] = buffer[(short) ISO7816.OFFSET_CDATA+j];
                    }
                    
                    return;
                }
            }
            
            ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
        }
    }
    
    // readRercord (P1: record_index, P2: response_length)
    void readRecord(APDU apdu) {
        byte[] buffer = apdu.getBuffer();
        if (selectedFile == (short) 0xFF02) {
            if (buffer[ISO7816.OFFSET_P1] >= FF02_NUMBER_OF_RECORDS) ISOException.throwIt(ISO7816.SW_RECORD_NOT_FOUND);
            if (buffer[ISO7816.OFFSET_CDATA] > FF02_RECORD_LEN) ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
            
            byte response_len = buffer[ISO7816.OFFSET_P2];
            
            apdu.setOutgoing();
            apdu.setOutgoingLength(response_len);
            apdu.sendBytesLong(FF02, (byte) 0, response_len);
            
        } else if (selectedFile == (short) 0xFF03) {
            if (buffer[ISO7816.OFFSET_P1] >= FF03_NUMBER_OF_RECORDS) ISOException.throwIt(ISO7816.SW_RECORD_NOT_FOUND);
            if (buffer[ISO7816.OFFSET_P2] > FF03_RECORD_LEN) ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
            
            byte record_indx = buffer[ISO7816.OFFSET_P1];
            byte response_len = buffer[ISO7816.OFFSET_P2];
            
            apdu.setOutgoing();
            apdu.setOutgoingLength(response_len);
            apdu.sendBytesLong(FF03, (short) (record_indx*FF03_RECORD_LEN), response_len);
            
        } else if (selectedFile == (short) 0xFF04) {
            if (buffer[ISO7816.OFFSET_P1] >= FF04_NUMBER_OF_RECORDS) ISOException.throwIt(ISO7816.SW_RECORD_NOT_FOUND);
            if (buffer[ISO7816.OFFSET_P2] > FF04_RECORD_LEN) ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
            
            byte record_indx = buffer[ISO7816.OFFSET_P1];
            byte response_len = buffer[ISO7816.OFFSET_P2];
            
            apdu.setOutgoing();
            apdu.setOutgoingLength(response_len);
            apdu.sendBytesLong(FF04, (short) (record_indx*FF04_RECORD_LEN), response_len);
            
        } else {
            short userFileId;
            for (byte i = 0; i < FF02[0]; i++) {
                userFileId = Util.getShort(FF04, (short) (FF04_RECORD_LEN*i + 4));
                if (userFileId == selectedFile) {
                    if (buffer[ISO7816.OFFSET_P1] >= FF04[(short) (FF04_RECORD_LEN*i+1)]) ISOException.throwIt(ISO7816.SW_RECORD_NOT_FOUND);
                    if (buffer[ISO7816.OFFSET_P2] != FF04[(short) (FF04_RECORD_LEN*i)]) ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
                    
                    byte record_indx = buffer[ISO7816.OFFSET_P1];
                    byte file_record_len = FF04[(short) (FF04_RECORD_LEN*i)];
                    byte response_len = buffer[ISO7816.OFFSET_P2];
            
                    apdu.setOutgoing();
                    apdu.setOutgoingLength(response_len);
                    apdu.sendBytesLong(((byte[]) UserFile[i]), (short) (record_indx*file_record_len), response_len);
                    
                    return;
                }
            }
            
            ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
        }
    }
    
    void submitCode(APDU apdu) {
        byte[] buffer = apdu.getBuffer();
        switch (buffer[ISO7816.OFFSET_P1]) {
            case (byte) 6:
                verifyPIN(apdu);
                break;
            case (byte) 7:
                verifyIC(apdu);
                break;
            default:
                ISOException.throwIt(ISO7816.SW_WRONG_P1P2);
        }
    }
    
    void verifyPIN(APDU apdu) {
        byte[] buffer = apdu.getBuffer();
        if (buffer[ISO7816.OFFSET_LC] != PIN_LENGHT) ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        
        if (PIN.getTriesRemaining() > 0) {
            if (!PIN.check(buffer, ISO7816.OFFSET_CDATA, PIN_LENGHT))
                ISOException.throwIt((short) (SW_WRONG_CODE + PIN.getTriesRemaining()));
        } else {
            pinBloque = true;
            ISOException.throwIt(SW_CODE_BLOQUE);
        }
    }
    
    void verifyIC(APDU apdu) {
        byte[] buffer = apdu.getBuffer();
        if (buffer[ISO7816.OFFSET_LC] != IC_LENGHT) ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        
        if (IC.getTriesRemaining() > 0) {
            if (!IC.check(buffer, ISO7816.OFFSET_CDATA, IC_LENGHT))
                ISOException.throwIt((short) (SW_WRONG_CODE + IC.getTriesRemaining()));
        } else {
            ICBloque = true;
            ISOException.throwIt(SW_CODE_BLOQUE);
        }
    }
    
    

    public boolean select() {
        return true;
    }
}

