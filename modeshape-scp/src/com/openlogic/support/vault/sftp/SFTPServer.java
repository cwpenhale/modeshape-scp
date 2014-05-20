package com.openlogic.support.vault.sftp;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.inject.Named;

import org.apache.sshd.SshServer;
import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.server.Command;
import org.apache.sshd.server.command.ScpCommandFactory;
import org.apache.sshd.server.jaas.JaasPasswordAuthenticator;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.sftp.subsystem.SftpSubsystem;

@Named
@Startup
@Singleton
public class SFTPServer implements Serializable{
	
	public static final String JCR_JNDI_URI = "java:/jcr/sample";
	public static final String WORKSPACE = "default";
	
	
	private static final long serialVersionUID = 2944049355962032146L;
	private SshServer sshd;


	@PostConstruct
	public void setupSftpServer(){
		sshd = SshServer.setUpDefaultServer();
		JaasPasswordAuthenticator pswdAuth = new JcrCredentialCallbackJaasPasswordAuthenticator();
		pswdAuth.setDomain("modeshape-security");
		sshd.setPasswordAuthenticator(pswdAuth);
	    sshd.setPort(2060);
	    sshd.setKeyPairProvider(new SimpleGeneratorHostKeyProvider("hostkey.ser"));

	    sshd.setCommandFactory(new ScpCommandFactory());

	    List<NamedFactory<Command>> namedFactoryList = new ArrayList<NamedFactory<Command>>();
	    namedFactoryList.add(new SftpSubsystem.Factory());
	    sshd.setSubsystemFactories(namedFactoryList);
	    sshd.setFileSystemFactory(new JCRFileSystemFactory());
        try {
			sshd.start();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	@PreDestroy
	public void tearDownSftpServer(){
		try {
			sshd.stop();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	

}
