package com.gpl.rpg.atcontentstudio.utils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class FileUtils {

	public static void deleteDir(File dir) {
		if (dir.exists()) {
			for (File f : dir.listFiles()) {
				if (f.isDirectory()) {
					deleteDir(f);
				} else {
					f.delete();
				}
			}
			dir.delete();
		}
	}
	
	public static void copyFile(File sourceLocation , File targetLocation) {
		try {
			InputStream in = new FileInputStream(sourceLocation);
			OutputStream out = new FileOutputStream(targetLocation);

			// Copy the bits from instream to outstream
			byte[] buf = new byte[1024];
			int len;
			try {
				while ((len = in.read(buf)) > 0) {
					out.write(buf, 0, len);
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
			} finally {
				try {
					in.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
				}
				try {
					out.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
				}
			}
		} catch (FileNotFoundException e1) {
			// TODO Auto-generated catch block
		}
	}
	
	private static final int BUFFER = 2048;
	public static void writeToZip(File folder, File target) {
		try {
	        FileOutputStream dest = new FileOutputStream(target);
	        ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(dest));
	        zipDir(folder, "", out);	        
	        out.flush();
	        out.close();
	    } catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
	    }

	}
	
	/**
	 * cp sourceFolder/* targetFolder/
	 * @param sourceFolder
	 * @param targetFolder
	 */
	public static void copyOver(File sourceFolder, File targetFolder) {
		if (!sourceFolder.isDirectory() || !targetFolder.isDirectory()) return;
		for (File f : sourceFolder.listFiles()) {
			if (Files.isSymbolicLink(f.toPath())) {
				//Skip symlinks
				continue;
			} else if (f.isDirectory()) {
				File dest = new File(targetFolder, f.getName());
				if (!dest.exists()) {
					dest.mkdir();
				}
				copyOver(f, dest);
			} else {
				copyFile(f, new File(targetFolder, f.getName()));
			}
		}
	}
	
	private static void zipDir(File dir, String prefix, ZipOutputStream zos) {
		if (prefix != "") {
			prefix = prefix + File.separator;
		}
		for (File f : dir.listFiles()) {
			if (f.isDirectory()) {
				zipDir(f, prefix+f.getName(), zos);
			} else {
				FileInputStream fis;
				try {
					fis = new FileInputStream(f);
					BufferedInputStream origin = new BufferedInputStream(fis, BUFFER);
					ZipEntry entry = new ZipEntry(prefix+f.getName());
					try {
						zos.putNextEntry(entry);
						int count;
						byte data[] = new byte[BUFFER];
						while ((count = origin.read(data, 0, BUFFER)) != -1) {
							zos.write(data, 0, count);
							zos.flush();
						}
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} finally {
						try {
							origin.close();
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
				} catch (FileNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}
	
	public static boolean makeSymlink(File targetFile, File linkFile) {
		Path target = Paths.get(targetFile.getAbsolutePath());
		Path link = Paths.get(linkFile.getAbsolutePath());
		if (!Files.exists(link)) {
			try {
				Files.createSymbolicLink(link, target);
			} catch (Exception e) {
				System.err.println("Failed to create symbolic link to target \""+targetFile.getAbsolutePath()+"\" as \""+linkFile.getAbsolutePath()+"\" the java.nio way:");
				e.printStackTrace();
				switch (DesktopIntegration.detectedOS) {
				case Windows:
					System.err.println("Trying the Windows way with mklink");
					try {
						Runtime.getRuntime().exec("cmd.exe /C mklink "+(targetFile.isDirectory() ? "/J " : "")+"\""+linkFile.getAbsolutePath()+"\" \""+targetFile.getAbsolutePath()+"\"");
					} catch (IOException e1) {
						e1.printStackTrace();
					}
					if (!linkFile.exists()) {
						System.err.println("Attempting UAC elevation through VBS script.");
						runWithUac("cmd.exe /C mklink "+(targetFile.isDirectory() ? "/J " : "")+"\""+linkFile.getAbsolutePath()+"\" \""+targetFile.getAbsolutePath()+"\"", 3, linkFile);
					}
					break;
				case MacOS:
				case NIX:
				case Other:
					System.err.println("Trying the unix way with ln -s");
					try {
						Runtime.getRuntime().exec("ln -s "+targetFile.getAbsolutePath()+" "+linkFile.getAbsolutePath());
					} catch (IOException e1) {
						e1.printStackTrace();
					}
					break;
				default:
					System.out.println("Unrecognized OS. Please contact ATCS dev.");
					break;
					
				}
			}
		}
		if (!Files.exists(link)) {
			System.err.println("Failed to create link \""+linkFile.getAbsolutePath()+"\" targetting \""+targetFile.getAbsolutePath()+"\"");
			System.err.println("You can try running ATCS with administrative privileges once, or create the symbolic link manually.");
		}
		return true;
	}

	public static File backupFile(File f) {
		try {
			Path returned = Files.copy(Paths.get(f.getAbsolutePath()), Paths.get(f.getAbsolutePath()+".bak"), StandardCopyOption.REPLACE_EXISTING);
			return returned.toFile();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	static final String uacBatName = "ATCS_elevateWithUac.bat";
	public static void runWithUac(String command, int tries, File checkExists) {
		File tmpFolder = new File(System.getProperty("java.io.tmpdir"));
		File batFile = new File(tmpFolder, uacBatName);
		batFile.deleteOnExit();
		FileWriter writer;
		try {
			writer = new FileWriter(batFile, false);
			writer.write(
				"@echo Set objShell = CreateObject(\"Shell.Application\") > %temp%\\sudo.tmp.vbs\r\n"
				+ "@echo args = Right(\"%*\", (Len(\"%*\") - Len(\"%1\"))) >> %temp%\\sudo.tmp.vbs\r\n"
				+ "@echo objShell.ShellExecute \"%1\", args, \"\", \"runas\" >> %temp%\\sudo.tmp.vbs\r\n"
				+ "@cscript %temp%\\sudo.tmp.vbs\r\n"
				+ "del /f %temp%\\sudo.tmp.vbs\r\n");
			writer.close();
			while (!checkExists.exists() && tries-- > 0) {
				Runtime.getRuntime().exec(new String[]{"cmd.exe","/C", batFile.getAbsolutePath()+" "+command});
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}
	
}
