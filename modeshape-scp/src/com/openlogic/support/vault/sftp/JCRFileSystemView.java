package com.openlogic.support.vault.sftp;

import org.apache.sshd.common.file.FileSystemView;
import org.apache.sshd.common.file.SshFile;
import org.jboss.logging.Logger;


public class JCRFileSystemView implements FileSystemView {
	
	private final static Logger log = Logger.getLogger(JCRFileSystemView.class);
	
	private String username;
	private String password;
	
	
	@Override
	public SshFile getFile(String pathArgument) {
		log.infof("Getting %s in JCRFileSystemView for %s", pathArgument, getUsername());
		JCRSSHFile file = new JCRSSHFile(this, pathArgument);
		log.infof("Got %s in JCRFileSystemView for %s", pathArgument, getUsername());
		return file;
	}

	@Override
	public SshFile getFile(SshFile arg0, String pathArgument) {
		log.infof("getFile 2-arg called in JCRFileSystemView for %s", getUsername());
		return getFile(arg0.getAbsolutePath());
	}

	@Override
	public FileSystemView getNormalizedView() {
		log.infof("getNormalizedView called in JCRFileSystemView for %s", getUsername());
		return null;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

}
