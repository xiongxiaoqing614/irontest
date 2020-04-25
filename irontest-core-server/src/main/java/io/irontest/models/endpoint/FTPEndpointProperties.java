package io.irontest.models.endpoint;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;
import io.irontest.models.Properties;
import io.irontest.resources.ResourceJsonViews;

public class FTPEndpointProperties extends Properties {
    @JsonView({ResourceJsonViews.TeststepEdit.class, ResourceJsonViews.TestcaseExport.class})
    private String host;
    @JsonView({ResourceJsonViews.TeststepEdit.class, ResourceJsonViews.TestcaseExport.class})
    private Integer port;

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    /**
     * Used to unify FTP URL display on test step action tab and test case run report.
     * @return
     */
    @JsonProperty
    @JsonView(ResourceJsonViews.TeststepEdit.class)
    public String getFtpURL() {
        return "ftp://" + host + ":" + port;
    }

    @JsonIgnore
    public void setFtpURL(String ftpURL) {
        //  do nothing
    }
}