package com.openlogic.support.vault.sftp;

import org.apache.sshd.common.Session.AttributeKey;
import org.apache.sshd.server.jaas.JaasPasswordAuthenticator;
import org.apache.sshd.server.session.ServerSession;

public class JcrCredentialCallbackJaasPasswordAuthenticator extends JaasPasswordAuthenticator {
	
	public final static AttributeKey<String> PASSWORD_KEY = new AttributeKey<String>();
	
	@Override
	public boolean authenticate(String username, String password, ServerSession session) {
		session.setAttribute(PASSWORD_KEY, password);
		return super.authenticate(username, password, session);
	}
	
	

}
