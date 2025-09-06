package com.ramide1.mcllm;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;

public class HttpRequest {
	private String url;
	private String method;
	private String contentType;
	private String authorization;
	private String data;
	private String response;
	private boolean error;

	public HttpRequest(String url, String method, String contentType, String authorization, String data) {
		this.url = url;
		this.method = method;
		this.contentType = contentType;
		this.authorization = authorization;
		this.data = data;
		this.response = method + " request did not send.";
		this.error = true;
	}

	public void sendRequest() {
		try {
			URI obj = new URI(url);
			HttpURLConnection con = (HttpURLConnection) obj.toURL().openConnection();
			con.setRequestMethod(method);
			con.setRequestProperty("Content-Type", contentType);
			con.setRequestProperty("Authorization", authorization);
			if (method == "POST") {
				con.setDoOutput(true);
				OutputStream os = con.getOutputStream();
				os.write(data.getBytes());
				os.flush();
				os.close();
			}
			int responseCode = con.getResponseCode();
			if (responseCode != HttpURLConnection.HTTP_OK)
				throw new Exception(method + " request did not work.");
			BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
			String inputLine;
			StringBuffer responseBuffer = new StringBuffer();
			while ((inputLine = in.readLine()) != null) {
				responseBuffer.append(inputLine);
			}
			in.close();
			error = false;
			response = responseBuffer.toString();
		} catch (Exception e) {
			error = true;
			response = e.getMessage();
		}
	}

	public String getResponse() {
		return response;
	}

	public boolean getError() {
		return error;
	}
}