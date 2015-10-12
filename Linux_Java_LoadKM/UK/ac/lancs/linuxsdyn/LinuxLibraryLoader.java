package UK.ac.lancs.linuxsdyn;

import java.io.*;
import java.net.*;
import java.security.*;
import java.util.*;

import UK.ac.lancs.nativecode.*;

class LinuxLibraryLoader implements LibraryLoader {
  private String chmod;
  private ClassLoader loader;

  public LinuxLibraryLoader(ClassLoader loader, Properties props) {
    this.loader = loader;
    this.chmod = props.getProperty("linux.chmod", "/bin/chmod");
  }

  public void loadLibrary(String resource) throws InstallationException {
    try {
      URL loc = loader.getResource(resource);
      if (loc == null)
	throw new
	  InstallationException("Resource " + resource + " unavailable");

      // check that we can do this
      Permission p = new NativePermission(loc.toString(), "library");
      AccessController.checkPermission(p);

      InputStream source = loc.openStream();
	if (source == null)
	  throw new InstallationException("Resource " +
					  resource + "unavailable");

      try {
	final File file = (File)
	  AccessController.doPrivileged(new PrivilegedExceptionAction() {
	      public Object run() throws IOException {
		return File.createTempFile("lin", ".so");
	      }
	    });

	OutputStream dest = (OutputStream)
	  AccessController.doPrivileged(new PrivilegedExceptionAction() {
	      public Object run() throws IOException {
		return new FileOutputStream(file);
	      }
	    });

	byte[] buf = new byte[1024];
	int len;

	while ((len = source.read(buf)) != -1)
	  dest.write(buf, 0, len);
	dest.close();

	final String name = file.getAbsolutePath();
	AccessController.doPrivileged(new PrivilegedExceptionAction() {
	    public Object run() throws IOException, InterruptedException {
	      Runtime.getRuntime().exec(chmod + " 555 " + name).waitFor();
	      System.load(name);

	      // TO DO: delete library

	      return null;
	    }
	  });
      } catch (PrivilegedActionException ex) {
	Exception ix = ex.getException();
	if (ix instanceof IOException)
	  throw (IOException) ix;
	if (ix instanceof InterruptedException)
	  throw (InterruptedException) ix;
      }
    } catch (IOException ex) {
      throw new InstallationException("I/O error: " + ex.getMessage());
    } catch (InterruptedException ex) {
      throw new InstallationException("Thread error: " + ex.getMessage());
    }
  }
}
