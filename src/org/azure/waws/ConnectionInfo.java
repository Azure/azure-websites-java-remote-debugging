package org.azure.waws;

public class ConnectionInfo {
	private int localPort = -1;
	private String siteUrl = "";
	private String userName = "";
	private String password = "";
	private boolean useBasicAuth = false;
	private boolean ssl = false;
	private String affinity = "";
	
	public int getLocalPort() {
		return localPort;
	}
	public void setLocalPort(int localPort) {
		this.localPort = localPort;
	}
	public String getSiteUrl() {
		return siteUrl;
	}
	public void setSiteUrl(String siteUrl) {
		this.siteUrl = siteUrl;
	}
	public String getUserName() {
		return userName;
	}
	public void setUserName(String userName) {
		this.userName = userName;
		this.useBasicAuth = true;
		this.ssl = true;
	}
	public String getPassword() {
		return password;
	}
	public void setPassword(String password) {
		this.password = password;
		this.useBasicAuth = true;
		this.ssl = true;
	}
	public boolean useBasicAuth() {
		return useBasicAuth;
	}
	public boolean isSsl() {
		return ssl;
	}
	public String getAffinity() {
		return affinity;
	}
	public void setAffinity(String affinity) {
		this.affinity = affinity;
	}
}
