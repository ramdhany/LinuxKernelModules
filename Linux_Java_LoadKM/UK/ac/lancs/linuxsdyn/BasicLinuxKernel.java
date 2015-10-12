package UK.ac.lancs.linuxsdyn;

import java.io.*;
import java.net.*;
import java.util.*;
import java.security.*;

import UK.ac.lancs.nativecode.*;

class BasicLinuxKernel implements CharDeviceLoader, ModuleLoader {
	static int findDeviceNumber(String name) {
		try {
			BufferedReader in = null;

			try {
				in = (BufferedReader)
				AccessController.doPrivileged(new PrivilegedExceptionAction() {
					public Object run() throws IOException {
						return new BufferedReader(new FileReader("/proc/devices"));
					}
				});
			} catch (PrivilegedActionException ex) {
				throw (IOException) ex.getException();
			}

			String line;
			boolean blockDevice = true;

			while ((line = in.readLine()) != null) {
				if (line.startsWith("Character ")) {
					blockDevice = false;
				} else if (line.startsWith("Block ")) {
					blockDevice = true;
				} else {
					try {
						StringTokenizer tokens = new StringTokenizer(line);
						String number = tokens.nextToken();
						if (tokens.nextToken().equals(name)) {
							in.close();
							int value = Integer.parseInt(number);
							return blockDevice ? -value : value;
						}
					} catch (NoSuchElementException ex) {
					}
				}
			}

			in.close();
		} catch (FileNotFoundException ex) {
			System.err.println("Device list not found!");
		} catch (IOException ex) {
			//System.err.println("Device list not found!");
		}
		return 0;
	}

	java.lang.ClassLoader loader = null;
	java.util.Properties props = null;
	java.util.Set devices = new java.util.HashSet();
	java.util.Set modules = new java.util.HashSet();

	public BasicLinuxKernel(java.lang.ClassLoader loader,
			java.util.Properties props) {
		this.loader = loader;
		this.props = props;
	}

	public Module loadModule(String resource)
	throws InstallationException {
		try {
			// find out where we're loading the code from
			URL loc = loader.getResource(resource);
			if (loc == null)
				throw new InstallationException("Module download: not found");

			// check that we can do this
			Permission p = new NativePermission(loc.toString(), "module");
			AccessController.checkPermission(p);

			// get ready to download code
			InputStream is = loc.openStream();
			if (is == null)
				throw new InstallationException("Module download: not found");

			ModuleReference ref = new ModuleReference(is);
			synchronized (modules) {
				modules.add(ref);
			}
			return ref;
		} catch (IOException ex) {
			throw new InstallationException("Module download: " + ex.getMessage());
		}
	}

	public CharDevice loadCharDevice(String resource, int minor)
	throws InstallationException {
		try {
			// find out where we're loading the code from
			URL loc = loader.getResource(resource);
			if (loc == null)
				throw new InstallationException("Module download: not found");

			// check that we can do this
			Permission p = new NativePermission(loc.toString(), "chardev");
			AccessController.checkPermission(p);

			// get ready to download code
			InputStream is = loc.openStream();
			if (is == null)
				throw new InstallationException("Module download: not found");

			DeviceReference ref = new DeviceReference(is, minor);
			synchronized (devices) {
				devices.add(ref);
			}
			return ref;
		} catch (IOException ex) {
			throw new InstallationException("Module download: " + ex.getMessage());
		}
	}

	public void finalize() {
		Set myDevices, myModules;

		synchronized (this) {
			myDevices = devices;
			devices = null;

			myModules = modules;
			modules = null;
		}

		if (myDevices != null) {
			synchronized (myDevices) {
				for (Iterator iter = myDevices.iterator();
				iter.hasNext(); iter.remove())
					((DeviceReference) iter.next()).discard();
			}
		}

		if (myModules != null) {
			synchronized (myModules) {
				for (Iterator iter = myModules.iterator();
				iter.hasNext(); iter.remove())
					((ModuleReference) iter.next()).discard();
			}
		}
	}

	private static final String insertModuleProperty = "insmod";
	private static final String removeModuleProperty = "rmmod";
	private static final String makeNodeProperty = "mknod";

	private class DeviceReference implements CharDevice {
		private String moduleName = null;
		private File deviceFile;
		private RandomAccessFile dataStream;

		public void finalize() {
			discard();
		}

		public void discard() {
			final String removeModule = props.getProperty(removeModuleProperty);

			if (removeModule == null)
				return;

			/* Get a handle on the runtime */
			Runtime rt = Runtime.getRuntime();

			if (moduleName != null) {
				/* Do rmmod */
				try {
					inStream.close();
					outStream.close();

					if (dataStream != null) {
						dataStream.close();
						dataStream = null;
					}

					if (deviceFile != null) {
						AccessController.doPrivileged(new FileDeletion(deviceFile));
						deviceFile = null;
					}

					if (moduleName != null) {
						/* Create the complete command for insmod */
						String[] command = new String[] {
								removeModule, moduleName
						};

						//for (int i = 0; i < command.length; i++)
						//System.out.print(" " + command[i]);
						//System.out.println("");

						try {
							CommandExecution com = new CommandExecution(rt, command);
							AccessController.doPrivileged(com);
						} catch (PrivilegedActionException ex) {
							throw (IOException) ex.getException();
						}
						moduleName = null;
					}
				} catch (IOException e) {
					System.err.println("IO exception: " + e.getMessage());
				}
			}
		}

		public DeviceReference(InputStream resourceIn, int minor)
		throws InstallationException {
			final String insertModule = props.getProperty(insertModuleProperty);
			final String makeNode = props.getProperty(makeNodeProperty);

			if (insertModule == null)
				throw new InstallationException("Module insertion: " +
						insertModuleProperty + " unset");

			if (makeNode == null)
				throw new InstallationException("Module access: " +
						makeNodeProperty + " unset");

			Runtime rt = Runtime.getRuntime();
			File modFile;

			try {
				/* Create some arbitrary file name */
				try {
					modFile = (File)
					AccessController.doPrivileged(new TempCreation("lin",".o"));

					class OutOpening implements PrivilegedExceptionAction {
						public OutOpening(File f) {
							this.f = f;
						}
						File f;
						public Object run() throws IOException {
							return new FileOutputStream(f);
						}
					}

					OutputStream fileOut = (OutputStream)
					AccessController.doPrivileged(new OutOpening(modFile));

					byte[] buffer = new byte[1024];
					int length;

					/* Write the resource to a temp file */
					while ((length = resourceIn.read(buffer)) != -1)
						fileOut.write(buffer, 0, length);

					resourceIn.close();
					fileOut.close();
				} catch (PrivilegedActionException ex) {
					throw (IOException) ex.getException();
				}
			} catch (IOException e) {
				discard();
				throw new
				InstallationException("Module download: " + e.getMessage());
			}

			try {
				/* Extract the module name */
				String moduleName = modFile.getName();
				moduleName = moduleName.substring(0,moduleName.lastIndexOf(".o"));

				/* Create the complete command for insmod */
				String[] command = new String[] {
						insertModule, "-f", "-k", "-o", moduleName,
						modFile.getAbsolutePath(), "devname=" + moduleName
				};

				/* Do the insmod */
				try {
					Process process = (Process)
					AccessController.doPrivileged(new CommandExecution(rt, command));
					process.waitFor();

					if (process.exitValue() != 0) {
						discard();
						throw new
						InstallationException("Module insertion: command failed");
					}
				} catch (PrivilegedActionException ex) {
					throw (IOException) ex.getException();
				}
				this.moduleName = moduleName;
			} catch (java.io.IOException e) {
				discard();
				throw new InstallationException("Module insertion: " + e.getMessage());
			} catch (InterruptedException e) {
				discard();
				throw new InstallationException("Module insertion interrupted");
			} finally {
				AccessController.doPrivileged(new FileDeletion(modFile));
			}

			try {
				/* Create a temp file for mknod and instantiate the Random
	   Access File */
				try {
					deviceFile = (File)
					AccessController.doPrivileged(new TempCreation("lin",".dev"));
					AccessController.doPrivileged(new FileDeletion(deviceFile));

					int deviceNumber = findDeviceNumber(moduleName);
					String deviceType = "c";
					//if (deviceNumber < 0) {
					//deviceType = "b";
					//deviceNumber = -deviceNumber;
					//}

					/* Create the complete command for mknod */
					String[] command = new String[] {
							makeNode, deviceFile.getAbsolutePath(), deviceType,
							Integer.toString(deviceNumber), Integer.toString(minor)
					};

					Process process = (Process)
					AccessController.doPrivileged(new CommandExecution(rt, command));
					process.waitFor();

					if (process.exitValue() != 0) {
						discard();
						throw new InstallationException("Module access: command failed");
					}

					class FileOpening implements PrivilegedExceptionAction {
						public FileOpening(File f) {
							this.f = f;
						}

						File f;

						public Object run() throws IOException {
							return new RandomAccessFile(f, "rw");
						}
					}

					dataStream = (RandomAccessFile)
					AccessController.doPrivileged(new FileOpening(deviceFile));
				} catch (PrivilegedActionException ex) {
					throw (IOException) ex.getException();
				}
			} catch (IOException e) {
				discard();
				throw new InstallationException("Module access: " + e.getMessage());
			} catch (InterruptedException e) {
				discard();
				throw new InstallationException("Module access: " + e.getMessage());
			}
		}

		class OutStream extends OutputStream { 
			public void write(int b) throws IOException {
				dataStream.write(b);
			} // end write method       
		}

		class InStream extends InputStream {
			public int read() throws IOException {
				for ( ; ; )
					try {
						return dataStream.read();
					} catch (IOException ex) {
						if (!ex.getMessage().equals("Interrupted system call"))
							throw ex;
					}
			} // end read method
		}

		OutStream outStream = new OutStream();
		InStream inStream = new InStream();

		public InputStream getInputStream() {
			return inStream;
		}

		public OutputStream getOutputStream() {
			return outStream;
		}
	} 


	private class CommandExecution implements PrivilegedExceptionAction {
		public CommandExecution(Runtime rt, String[] command) {
			this.rt = rt;
			this.command = command;
		}

		private Runtime rt;
		private String[] command;

		public Object run() throws IOException {
			//System.err.print("Executing command:");
			//for (int i = 0; i < command.length; i++)
			//System.err.print(" " + command[i]);
			//System.err.println();
			return rt.exec(command);
		}
	}

	private class TempCreation implements PrivilegedExceptionAction {
		public TempCreation(String pre, String suf) {
			this.pre = pre;
			this.suf = suf;
		}

		String pre, suf;

		public Object run() throws IOException {
			return File.createTempFile(pre, suf);
		}
	}

	private class FileDeletion implements PrivilegedAction {
		public FileDeletion(File f) {
			this.f = f;
		}

		private File f;

		public Object run() {
			return new Boolean(f.delete());
		}
	}


	private class ModuleReference implements Module {
		private String moduleName = null;
		private File deviceFile;
		private RandomAccessFile dataStream;

		public void finalize() {
			discard();
		}

		public void discard() {
			final String removeModule = props.getProperty(removeModuleProperty);

			if (removeModule == null)
				return;

			/* Get a handle on the runtime */
			Runtime rt = Runtime.getRuntime();

			if (moduleName != null) {
				/* Do rmmod */
				try {
					if (moduleName != null) {
						/* Create the complete command for insmod */
						String[] command = new String[] {
								removeModule, moduleName
						};

						for (int i = 0; i < command.length; i++)
							System.out.print(" " + command[i]);
						System.out.println("");

						try {
							CommandExecution com = new CommandExecution(rt, command);
							AccessController.doPrivileged(com);
						} catch (PrivilegedActionException ex) {
							throw (IOException) ex.getException();
						}
						moduleName = null;
					}
				} catch (IOException e) {
					System.err.println("IO exception: " + e.getMessage());
				}
			}
		}

		public ModuleReference(InputStream resourceIn)
		throws InstallationException {
			final String insertModule = props.getProperty(insertModuleProperty);

			if (insertModule == null)
				throw new InstallationException("Module insertion: " +
						insertModuleProperty + " unset");

			Runtime rt = Runtime.getRuntime();
			File modFile;

			try {
				/* Create some arbitrary file name */
				try {
					modFile = (File)
					AccessController.doPrivileged(new TempCreation("lin",".o"));

					class OutOpening implements PrivilegedExceptionAction {
						public OutOpening(File f) {
							this.f = f;
						}
						File f;
						public Object run() throws IOException {
							return new FileOutputStream(f);
						}
					}

					OutputStream fileOut = (OutputStream)
					AccessController.doPrivileged(new OutOpening(modFile));

					byte[] buffer = new byte[1024];
					int length;

					/* Write the resource to a temp file */
					while ((length = resourceIn.read(buffer)) != -1)
						fileOut.write(buffer, 0, length);

					resourceIn.close();
					fileOut.close();
				} catch (PrivilegedActionException ex) {
					throw (IOException) ex.getException();
				}
			} catch (IOException e) {
				discard();
				throw new
				InstallationException("Module download: " + e.getMessage());
			}

			try {
				/* Extract the module name */
				String moduleName = modFile.getName();
				moduleName = moduleName.substring(0,moduleName.lastIndexOf(".o"));

				/* Create the complete command for insmod */
				String[] command = new String[] {
						insertModule, "-k", "-o", moduleName, modFile.getAbsolutePath()
				};

				/* Do the insmod */
				try {
					Process process = (Process)
					AccessController.doPrivileged(new CommandExecution(rt, command));
					process.waitFor();

					if (process.exitValue() != 0) {
						discard();
						throw new
						InstallationException("Module insertion: command failed");
					}
				} catch (PrivilegedActionException ex) {
					throw (IOException) ex.getException();
				}
				this.moduleName = moduleName;
			} catch (java.io.IOException e) {
				discard();
				throw new InstallationException("Module insertion: " + e.getMessage());
			} catch (InterruptedException e) {
				discard();
				throw new InstallationException("Module insertion interrupted");
			} finally {
				AccessController.doPrivileged(new FileDeletion(modFile));
			}
		}
	} 
}
