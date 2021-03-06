package com.linkedin.thirdeye.common;

import io.dropwizard.Configuration;

public abstract class ThirdEyeConfiguration extends Configuration {
  /**
   * Root directory for all other configuration
   */
  private String rootDir = "";
  /**
   * pinot/mysql etc. Impl specific file will be in
   * <configRootDir>/dataSources e.g
   * <configRootDir>/dataSources/pinot.yml
   */
  private String client = "pinot";
  /**
   * file, mysql etc
   * <configRootDir>/configStores/
   * <configRootDir>/configStores/file.yml
   */
  private String configStoreType = "FILE";

  private String whitelistCollections = "";
  private String blacklistCollections = "";

  private String detectorHost = "";
  private int detectorPort = 0;
  private String alertHost = "";
  private int alertPort = 0;

  private String smtpHost = "";
  private int smtpPort = 0;

  public String getRootDir() {
    return rootDir;
  }

  public void setRootDir(String rootDir) {
    this.rootDir = rootDir;
  }

  public String getClient() {
    return client;
  }

  public void setClient(String client) {
    this.client = client;
  }

  public String getConfigStoreType() {
    return configStoreType;
  }

  public void setConfigStoreType(String configStoreType) {
    this.configStoreType = configStoreType;
  }

  public String getWhitelistCollections() {
    return whitelistCollections;
  }

  public void setWhitelistCollections(String whitelistCollections) {
    this.whitelistCollections = whitelistCollections;
  }

  public String getBlacklistCollections() {
    return blacklistCollections;
  }

  public void setBlacklistCollections(String blacklistCollections) {
    this.blacklistCollections = blacklistCollections;
  }

  public String getFunctionConfigPath() {
    return getRootDir() + "/detector-config/anomaly-functions/functions.properties";
  }

  public String getDetectorHost() {
    return detectorHost;
  }

  public void setDetectorHost(String detectorHost) {
    this.detectorHost = detectorHost;
  }

  public int getDetectorPort() {
    return detectorPort;
  }

  public void setDetectorPort(int detectorPort) {
    this.detectorPort = detectorPort;
  }

  public String getAlertHost() {
    return alertHost;
  }

  public void setAlertHost(String alertHost) {
    this.alertHost = alertHost;
  }

  public int getAlertPort() {
    return alertPort;
  }

  public void setAlertPort(int alertPort) {
    this.alertPort = alertPort;
  }

  public String getSmtpHost() {
    return smtpHost;
  }

  public void setSmtpHost(String smtpHost) {
    this.smtpHost = smtpHost;
  }

  public int getSmtpPort() {
    return smtpPort;
  }

  public void setSmtpPort(int smtpPort) {
    this.smtpPort = smtpPort;
  }

}
