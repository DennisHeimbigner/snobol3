/*
 * @(#)StringReader.java	1.24 04/02/19
 *
 * Copyright 2004 Sun Microsystems, Inc. All rights reserved.
 * SUN PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package jsnobol3;

/**
 * A character stream whose source is a string buffer
 *
 * @version 	1.24, 04/02/19
 * @author	Mark Reinhold
 * @since	JDK1.1
 */

import java.io.Reader;
import java.io.IOException;

public class StringBufferReader extends Reader implements CharStream {

    protected StringBuffer str = new StringBuffer();
    protected int next = 0;
    protected int mark = 0;
    protected int lineno = 0;

    /**
     * Create a new string reader.
     *
     * @param s  StringBuffer providing the character stream.
     */
    public StringBufferReader() {super(); hardreset();}

    public StringBufferReader(String s) {this(); open(s);}

    /** Check to make sure that the stream has not been closed */
    protected void ensureOpen() throws IOException {
	if (str == null)
	    throw new IOException("Stream closed");
    }

    /**
     * Read a single character.
     *
     * @return     The character read, or -1 if the end of the stream has been
     *             reached
     *
     * @exception  IOException  If an I/O error occurs
     */
    public int read() throws IOException {
	synchronized (lock) {
	    ensureOpen();
	    if (next >= str.length())
		return (int)-1;
	    return (int)str.charAt(next++);
	}
    }

    /**
     * Read characters into a portion of an array.
     *
     * @param      cbuf  Destination buffer
     * @param      off   Offset at which to start writing characters
     * @param      len   Maximum number of characters to read
     *
     * @return     The number of characters read, or -1 if the end of the
     *             stream has been reached
     *
     * @exception  IOException  If an I/O error occurs
     */
    public int read(char cbuf[], int off, int len) throws IOException {
	synchronized (lock) {
	    ensureOpen();
            if ((off < 0) || (off > cbuf.length) || (len < 0) ||
                ((off + len) > cbuf.length) || ((off + len) < 0)) {
                throw new IndexOutOfBoundsException();
            } else if (len == 0) {
                return 0;
            }
	    if (next >= str.length())
		return (int)-1;
	    int n = Math.min(str.length() - next, len);
	    str.getChars(next, next + n, cbuf, off);
	    next += n;
	    return n;
	}
    }

    /**
     * Skips the specified number of characters in the stream. Returns
     * the number of characters that were skipped.
     *
     * <p>The <code>ns</code> parameter may be negative, even though the
     * <code>skip</code> method of the {@link Reader} superclass throws
     * an exception in this case. Negative values of <code>ns</code> cause the
     * stream to skip backwards. Negative return values indicate a skip
     * backwards. It is not possible to skip backwards past the beginning of
     * the string.
     *
     * <p>If the entire string has been read or skipped, then this method has
     * no effect and always returns 0.
     *
     * @exception  IOException  If an I/O error occurs
     */
    public long skip(long ns) throws IOException {
	synchronized (lock) {
            ensureOpen();
            if (next >= str.length())
                return 0;
            // Bound skip by beginning and end of the source
            long n = Math.min(str.length() - next, ns);
            n = Math.max(-next, n);
            next += n;
            return n;
        }
    }

    /**
     * Tell whether this stream is ready to be read.
     *
     * @return True if the next read() is guaranteed not to block for input
     *
     * @exception  IOException  If the stream is closed
     */
    public boolean ready() throws IOException {
        synchronized (lock) {
        ensureOpen();
        return true;
        }
    }

    /**
     * Tell whether this stream supports the mark() operation, which it does.
     */
    public boolean markSupported() {
	return true;
    }

    /**
     * Mark the present position in the stream.  Subsequent calls to reset()
     * will reposition the stream to this point.
     *
     * @param  readAheadLimit  Limit on the number of characters that may be
     *                         read while still preserving the mark.  Because
     *                         the stream's input comes from a string, there
     *                         is no actual limit, so this argument must not
     *                         be negative, but is otherwise ignored.
     *
     * @exception  IllegalArgumentException  If readAheadLimit is < 0
     * @exception  IOException  If an I/O error occurs
     */
    public void mark(int readAheadLimit) throws IOException {
	if (readAheadLimit < 0){
	    throw new IllegalArgumentException("Read-ahead limit < 0");
	}
	synchronized (lock) {
	    ensureOpen();
	    mark = next;
	}
    }

    /**
     * Reset the stream to the most recent mark, or to the beginning of the
     * string if it has never been marked.
     *
     * @exception  IOException  If an I/O error occurs
     */
    public void reset() throws IOException {
	synchronized (lock) {
	    ensureOpen();
	    next = mark;
	}
    }

    /**
     * Close the stream.
     */
    public void close() {
	str = null;
    }

    // Extra methods

    public void hardreset()
    {
	str.setLength(0);
	mark = 0;
	next = 0;
    }

    public StringBuffer getBuffer() {return str;}

    public void open(String s)
    {
	hardreset();	
	this.str.append(s);
    }

    // allow pushback
    public void pushback() throws IOException {pushback(1);}

    public void pushback(int n) throws IOException
    {
	if(next < n)
	    throw new IndexOutOfBoundsException("StringBufferReader: pushback failure");
	next -= n;
    }

    public Pos getPos() {return new Pos().set(lineno,next);}
    public void setLine(int l) {lineno=l;}

    // Obtain the remainder of the current line's text
    public String remainder() {return str.substring(next,str.length());}

    // CharStream Interface
    // allow peek == read+pushback
    public int peek()
    {
	try {int ch=read();pushback();return ch;
	} catch (IOException ioe) {return -1;}
    }
    public int getch()
    {
	try{return read();
	} catch (IOException ioe) {return -1;}
    }

}

