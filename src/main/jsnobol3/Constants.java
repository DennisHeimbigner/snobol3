package jsnobol3;

public abstract class Constants
{
    public static final char COMMENTCHAR='*'; 
    public static final char CONTINUECHAR='.'; 
    public static final char CONTROLWORDCHAR='-'; 
    public static final char COMMACHAR=','; 
    public static final char BLANKCHAR=' '; 
    public static final String WHITESPACECHARS =" \t"; 
    public static final String NULLSTRING =""; 
    public static final char SQUOTE = '\'';
    public static final char DQUOTE = '"';
    public static final char EOLCHAR = '\n';
    public static final int EOFCHAR = -1;
    public static final char  LPARENCHAR = '(';
    public static final char  RPARENCHAR = ')';
    public static final int NOADDRESS = -1;

    public static final int DEFAULTSTACKSIZE=128;
    public static final int INITIALCODESIZE=1024;

    public static final Integer ZERO = new Integer(0);

    // predefined IO streams
    public static final int STDNULL = -1;
    public static final int STDIN = 0;
    public static final int STDOUT = 1;
    public static final int STDERR = 2;
}
