package com.openlogic.support.vault.sftp;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.List;
import java.util.Map;

import javax.jcr.Binary;
import javax.jcr.LoginException;
import javax.jcr.NoSuchWorkspaceException;
import javax.jcr.Node;
import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;
import javax.jcr.security.AccessControlList;
import javax.jcr.security.AccessControlManager;
import javax.jcr.security.AccessControlPolicyIterator;
import javax.jcr.security.Privilege;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.apache.sshd.common.file.SshFile;
import org.jboss.logging.Logger;
import org.modeshape.jcr.api.JcrTools;
import org.modeshape.jcr.security.SimplePrincipal;

public class JCRSSHFile implements SshFile {
	
	private final static Logger log = Logger.getLogger(JCRFileSystemView.class);
	
	protected File file;
	
	private String id;
	private String path;
	private JcrTools jcrTools;
	private JCRFileSystemView jcrFileSystemView;
	
	public JCRSSHFile(JCRFileSystemView jcrFileSystemView, String pathArgument){
		log.info("Instantiating JCRSSHFile...");
		setJcrFileSystemView(jcrFileSystemView);
		setPath(pathArgument);
		setJcrTools(new JcrTools(true));//debug = true
		file = new File(getPath());
		log.infof("Instantiating JCRSSHFile for %s", getJcrFileSystemView().getUsername());
	}
	
	private Session getJcrSession() throws NamingException, LoginException, NoSuchWorkspaceException, RepositoryException{
		log.infof("Instantiating a JCRFileSystemView for %s", getJcrFileSystemView().getUsername());
		Context context = new InitialContext();
		log.infof("Got context at JCRFileSystemView for %s", getJcrFileSystemView().getUsername());
    	Repository repo = (Repository) context.lookup(SFTPServer.JCR_JNDI_URI);
    	log.infof("Got repo at JCRFileSystemView for %s", getJcrFileSystemView().getUsername());
    	return repo.login(new SimpleCredentials(getJcrFileSystemView().getUsername(), getJcrFileSystemView().getPassword().toCharArray()), SFTPServer.WORKSPACE);
	}
	
	private void applyAccessControlPolicies(Node node) throws Exception {
		Session jcrSession = node.getSession();
		log.infof("ACL %s", jcrSession.getUserID());
		String path = node.getPath();
		node.addMixin("mode:accessControllable");
		
		AccessControlManager acm = jcrSession.getAccessControlManager();

		// Convert the user privilege strings to Privilege instances ...
		String[] userPrivileges = new String[]{Privilege.JCR_READ, Privilege.JCR_WRITE};
		Privilege[] userPermissions = new Privilege[userPrivileges.length];
		for (int i = 0; i < userPrivileges.length; i++) {
			userPermissions[i] = acm.privilegeFromName(userPrivileges[i]);
		}

		// Convert the admin privilege strings to Privilege instances ...
		String[] adminPrivileges = new String[]{Privilege.JCR_ALL};
		Privilege[] adminPermissions = new Privilege[adminPrivileges.length];
		for (int i = 0; i < adminPrivileges.length; i++) {
			adminPermissions[i] = acm.privilegeFromName(adminPrivileges[i]);
		}

		AccessControlList acl = null;
		AccessControlPolicyIterator it = acm.getApplicablePolicies(path);
		if (it.hasNext()) {
			acl = (AccessControlList)it.nextAccessControlPolicy();
		} else {
			acl = (AccessControlList)acm.getPolicies(path)[0];
		}

		acl.addAccessControlEntry(SimplePrincipal.newInstance(jcrSession.getUserID()), userPermissions);
		acl.addAccessControlEntry(SimplePrincipal.newInstance("admin"), adminPermissions);	// grant admin user full control
		
		acm.setPolicy(path, acl);
	}

	@Override
	public boolean create() throws IOException {
		return true;
	}

	
	@Override
	public InputStream createInputStream(long arg0) throws IOException {
		log.info("createOutputStream called");
		Session jcrSession = null;
    	InputStream fileStream = null;
		try {
			jcrSession = getJcrSession();
			Node uploadNode = jcrSession.getNodeByIdentifier(getId());
	        if (uploadNode != null) {
	            Node uploadContent = uploadNode.getNode(javax.jcr.Node.JCR_CONTENT);
	            Binary uploadBinary = uploadContent.getProperty(javax.jcr.Property.JCR_DATA).getBinary();
	            fileStream = uploadBinary.getStream();
	        }
		} catch (LoginException e) {
			throw new IOException("Invalid Credentials for "+ getJcrFileSystemView().getUsername());
		} catch (NoSuchWorkspaceException e) {
			throw new IOException("No Such workspace "+ SFTPServer.WORKSPACE);
		} catch (RepositoryException e) {
			throw new IOException("Repository issue with  "+ SFTPServer.JCR_JNDI_URI);
		} catch (NamingException e) {
			throw new IOException("Naming issue with  "+ SFTPServer.JCR_JNDI_URI);
		} finally {
	        if (jcrSession !=null && jcrSession.isLive()) jcrSession.logout();			
		}
		log.info("createinputStream done");
		return fileStream;		
	}

	@Override
	public OutputStream createOutputStream(long offset) throws IOException {
		if(!doesExist()){
			if(!create()){
				throw new IOException("Cannot Create File");
			}
		}
		log.info("createOutputStream called");

        // permission check
        if (!isWritable()) {
            throw new IOException("No write permission : " + file.getName());
        }
        log.info("isWriteable passed");

        // move to the appropriate offset and create output stream
        final RandomAccessFile raf = new RandomAccessFile(file, "rw");
        log.info("RAF passed");
        try {
            raf.setLength(offset);
            raf.seek(offset);
            log.info("RAF seeked");

            // The IBM jre needs to have both the stream and the random access file
            // objects closed to actually close the file
            return new FileOutputStream(raf.getFD()) {
                public void close() throws IOException {
                	log.info("Closing FOS");
                	super.close();
                	raf.close();
                    try {
                		log.info("got sesh");
        		        final Node newUpload = getJcrTools().uploadFile(getJcrSession(), getPath(), file);
        		        log.info("made node");
        		        // set the meta-data for this upload
			        	log.info("set mixins, etc.");
        		        applyAccessControlPolicies(newUpload);
        		        log.infof("username: %s",newUpload.getSession().getUserID());
        		        newUpload.getSession().save();
        		        log.info("saved sesh");
        		        getJcrTools().printNode(newUpload);
        		        if(newUpload.getSession() !=null && newUpload.getSession().isLive())
        		        	newUpload.getSession().logout();
            		} catch (LoginException e) {
            			throw new IOException("Invalid Credentials for "+ getJcrFileSystemView().getUsername());
            		} catch (NoSuchWorkspaceException e) {
            			throw new IOException("No Such workspace "+ SFTPServer.WORKSPACE);
            		} catch (RepositoryException e) {
            			e.printStackTrace();
            			throw new IOException("Repository issue with  "+ SFTPServer.JCR_JNDI_URI);
            		} catch (NamingException e) {
            			throw new IOException("Naming issue with  "+ SFTPServer.JCR_JNDI_URI);
            		} catch (Exception e) {
						e.printStackTrace();
						throw new IOException("General issue with  "+ e.getMessage());
            		}
                    log.info("Closed FOS");
                }
            };
        } catch (IOException e) {
            raf.close();
            throw e;
        }
	}
	
	@Override
	public void createSymbolicLink(SshFile arg0) throws IOException {
		throw new IOException("NOT SUPPORTED");
	}

	@Override
	public boolean delete() {
		log.info("calling delete");
		return false;
	}

	@Override
	public boolean doesExist() {
		log.info("calling doesExist");
		if(getPath().equals("/")){
			return true;
		}else{
			return false;
		}
	}

	@Override
	public String getAbsolutePath() {
		// TODO Auto-generated method stub
		log.info("calling getAbsolutePath");
		return getPath();
	}

	@Override
	public Object getAttribute(Attribute arg0, boolean arg1) throws IOException {
		throw new IOException("NOT SUPPORTED");
	}

	@Override
	public Map<Attribute, Object> getAttributes(boolean arg0) throws IOException {
		throw new IOException("NOT SUPPORTED");
	}

	@Override
	public long getLastModified() {
		log.info("calling getLastModified");
		return 0;
	}

	@Override
	public String getName() {
		// TODO Auto-generated method stub
		log.info("calling getName");
		return null;
	}

	@Override
	public String getOwner() {
		// TODO Auto-generated method stub
		log.info("calling getOwner");
		return null;
	}

	@Override
	public SshFile getParentFile() {
		// TODO Auto-generated method stub
		log.info("calling getParentFile");
		JCRSSHFile parentFile = new JCRSSHFile(getJcrFileSystemView(), "/");
		log.info("created getParentFile");
		return parentFile;
	}

	@Override
	public long getSize() {
		// TODO Auto-generated method stub
		log.info("calling getSize");
		return 0;
	}

	@Override
	public void handleClose() throws IOException {
		log.info("calling handleClose");
		// TODO Auto-generated method stub

	}

	@Override
	public boolean isDirectory() {
		log.info("calling isDirectory");
		if(getPath().equals("/")){
			return true;
		}else{
			return false;
		}
	}

	@Override
	public boolean isExecutable() {
		log.info("calling isExecutable");
		return false;
	}

	@Override
	public boolean isFile() {
		log.info("calling isFile");
		return true;
	}

	@Override
	public boolean isReadable() {
		log.info("calling isReadable");
		return true;
	}

	@Override
	public boolean isRemovable() {
		log.info("calling isRemovable");
		return true;
	}

	@Override
	public boolean isWritable() {
		log.info("calling isWritable");
		return true;
	}

	@Override
	public List<SshFile> listSshFiles() {
		log.info("calling listSshFiles");
		return null;
	}

	@Override
	public boolean mkdir() {
		return false;
	}

	@Override
	public boolean move(SshFile arg0) {
		return false;
	}

	@Override
	public String readSymbolicLink() throws IOException {
		throw new IOException("NOT SUPPORTED");
	}

	@Override
	public void setAttribute(Attribute arg0, Object arg1) throws IOException {
		throw new IOException("NOT SUPPORTED");
	}

	@Override
	public void setAttributes(Map<Attribute, Object> arg0) throws IOException {
		throw new IOException("NOT SUPPORTED");
	}

	@Override
	public boolean setLastModified(long arg0) {
		return false;
	}

	@Override
	public void truncate() throws IOException {
		throw new IOException("NOT SUPPORTED");
	}

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public JcrTools getJcrTools() {
		return jcrTools;
	}

	public void setJcrTools(JcrTools jcrTools) {
		this.jcrTools = jcrTools;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public JCRFileSystemView getJcrFileSystemView() {
		return jcrFileSystemView;
	}

	public void setJcrFileSystemView(JCRFileSystemView jcrFileSystemView) {
		this.jcrFileSystemView = jcrFileSystemView;
	}

}