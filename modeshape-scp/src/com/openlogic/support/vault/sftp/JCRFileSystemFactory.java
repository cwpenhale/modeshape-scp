package com.openlogic.support.vault.sftp;

import java.io.IOException;

import org.apache.sshd.common.Session;
import org.apache.sshd.common.Session.AttributeKey;
import org.apache.sshd.common.file.FileSystemFactory;
import org.apache.sshd.common.file.FileSystemView;

public class JCRFileSystemFactory implements FileSystemFactory {
	
	
	@Override
	public FileSystemView createFileSystemView(Session session) throws IOException {
		JCRFileSystemView view = new JCRFileSystemView();
		view.setUsername(session.getUsername());
		view.setPassword(session.getAttribute(JcrCredentialCallbackJaasPasswordAuthenticator.PASSWORD_KEY));
		return view;
	}

}
