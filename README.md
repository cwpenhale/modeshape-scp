modeshape-scp
=============

This little project is a proof-of-concept for a way to connect your JAAS-enabled Modeshape JCR Repository to Apache Mina SSHD for sending (and receiving, eventually) files via SCP!

#### How to see that this works:

1. Configure Wildfly 8.0.0.Final to use Modeshape 4.0.0.Alpha3
2. Configure Wildfly to start with the provided standalone-modeshape.xml (modeshape-scp/standalone-modeshape.xml)
3. Ensure that the hard-coded port 2060 is available on your machine for the SSHServer to bind to
4. Start Wildfly, and browse the jcr/sample;default;admin:admin repository in with the modeshape-explorer WAR (http://localhost:8080/modeshape-explorer)
5. Repository: jcr/sample
6. Workspace: default
7. Username: admin
8. Password: admin
9. Notice that no content exists under the root JCR node
10. open a terminal on your local machine, and execute: "scp -P 2060 ~/Downloads/aFile admin@localhost:aFile"
11. enter the password "admin"
12. Notice the following output in $JBOSS_HOME/standalone/log/server.log:

```syslog
2014-05-20 07:40:50,005 INFO  [com.openlogic.support.vault.sftp.JCRFileSystemView] (ScpCommand: scp -t site.xml11) set mixins, etc.
2014-05-20 07:40:50,005 INFO  [com.openlogic.support.vault.sftp.JCRFileSystemView] (ScpCommand: scp -t site.xml11) ACL admin
2014-05-20 07:40:50,009 INFO  [com.openlogic.support.vault.sftp.JCRFileSystemView] (ScpCommand: scp -t site.xml11) username: admin
2014-05-20 07:40:50,019 INFO  [com.openlogic.support.vault.sftp.JCRFileSystemView] (ScpCommand: scp -t site.xml11) saved sesh
2014-05-20 07:40:50,020 INFO  [stdout] (ScpCommand: scp -t site.xml11)  site.xml11 jcr:primaryType=nt:file jcr:mixinTypes=[mode:accessControllable]
2014-05-20 07:40:50,021 INFO  [stdout] (ScpCommand: scp -t site.xml11)    - jcr:created=2014-05-20T07:40:50.009-06:00
2014-05-20 07:40:50,021 INFO  [stdout] (ScpCommand: scp -t site.xml11)    - jcr:createdBy="admin"
```

Hope this helps someone! Email me if you have questions or suggestions!

#### How it works:

1. SFTPServer starts up as a singleton, and sets up an Apache Mina SSHD server, which is configured to view the filesystem with JCRFileSystemFactory (sshd.setFileSystemFactory(new JCRFileSystemFactory());) and handle logins with JcrCredentialCallbackJaasPasswordAuthenticator (sshd.setPasswordAuthenticator(pswdAuth);)
2. Login attempts have their password saved to the session by JcrCredentialCallbackJaasPasswordAuthenticator
3. A file is uploaded (ScpCommand: scp -t aFile), which triggers the filesystem factory, which returns a JCRFileSystemView, which returns a JCRSSHFile
4. The method JCRSSHFile.createOutputStream(long) is called, which contains an anonymous inner-class based on FileOutputStream that, during the close() method, uses the JcrTools from Modeshape to upload the file into JCR, set ACLs, and print some information, closing the session when complete.


#### TODO:

1. Ask the Modeshape and Mina guys if there's a way to get the JAAS contexts to cooperate, so that one doesn't have to use JcrCredentialCallbackJaasPasswordAuthenticator.java
2. Download files via SCP
3. More functionality in JCRSSHFile.java


#### Credit:

This example was developed by Connor Penhale for OpenLogic, a Rogue Wave Company, and is released under the Apache Software License 2.0. You can find out more about OpenLogic, Rogue Wave, and their Open Source Software services at http://www.openlogic.com 
