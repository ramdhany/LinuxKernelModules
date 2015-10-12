package UK.ac.lancs.linuxsdyn;

import java.io.*;
import java.net.*;
import java.security.*;
import java.util.*;
import UK.ac.lancs.nativecode.*;

class LinuxProgramLoader implements ProgramLoader {
  private String chmod, chown;
  private ClassLoader loader;

  public LinuxProgramLoader(ClassLoader loader, Properties props) {
    this.loader = loader;
    this.chmod = props.getProperty("linux.chmod", "/bin/chmod");
    this.chown = props.getProperty("linux.chown", "/bin/chown");
  }

  public Program loadProgram(String resource) throws InstallationException {
    try {
      URL loc = loader.getResource(resource);
      if (loc == null)
	throw new
	  InstallationException("Resource " + resource + " unavailable");

      // check that we can do this
      Permission p = new NativePermission(loc.toString(), "program");
      AccessController.checkPermission(p);

      // get ready to download code
      InputStream is = loc.openStream();
      if (is == null)
	throw new
	  InstallationException("Resource " + resource + " unavailable");

      return new LinuxProgram(is);
    } catch (IOException ex) {
      throw new InstallationException("I/O error: " + ex.getMessage());
    }
  }

  private class LinuxProgram implements Program {
    File file;

    public Process exec(String[] args) throws ExecutionException {
      return exec(Runtime.getRuntime(), args);
    }

    public Process exec(String args) throws ExecutionException {
      return exec(Runtime.getRuntime(), args);
    }

    public Process exec(final Runtime rt, String[] args)
      throws ExecutionException {
      try {
	final String[] nargs = new String[args.length + 1];
	nargs[0] = file.getAbsolutePath();
	for (int i = 1; i < nargs.length; i++)
	  nargs[i] = args[i - 1];
	try {
	  return (Process)
	    AccessController.doPrivileged(new PrivilegedExceptionAction() {
		public Object run() throws IOException {
		  return rt.exec(nargs);
		}
	      });
	} catch (PrivilegedActionException ex) {
	  throw (IOException) ex.getException();
	}
      } catch (IOException ex) {
	throw new ExecutionException("I/O error: " + ex.getMessage());
      }
    }

    public Process exec(final Runtime rt, final String args)
      throws ExecutionException {
      try {
	try {
	  return (Process)
	    AccessController.doPrivileged(new PrivilegedExceptionAction() {
		public Object run() throws IOException {
		  return rt.exec(file.getAbsolutePath() + " " + args);
		}
	      });
	} catch (PrivilegedActionException ex) {
	  throw (IOException) ex.getException();
	}
      } catch (IOException ex) {
	throw new ExecutionException("I/O error: " + ex.getMessage());
      }
    }

    public void finalize() {
      AccessController.doPrivileged(new PrivilegedAction() {
	  public Object run() {
	    file.delete();
	    return null;
	  }
	});
      file = null;
    }

    public LinuxProgram(InputStream source) throws InstallationException {
      try {
	try {
	  file = (File)
	    AccessController.doPrivileged(new PrivilegedExceptionAction() {
		public Object run() throws IOException {
		  return File.createTempFile("lin", ".exe");
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
		Runtime rt = Runtime.getRuntime();
		rt.exec(chmod + " 6555 " + name).waitFor();
		rt.exec(chown + " nobody.nobody " + name).waitFor();
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
}
