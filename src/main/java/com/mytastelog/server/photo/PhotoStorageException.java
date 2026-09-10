package com.mytastelog.server.photo;

public class PhotoStorageException extends RuntimeException {
	public enum Operation { CONFIGURATION, UPLOAD, READ, DELETE }

	private final Operation operation;

	public PhotoStorageException(Operation operation, String message) {
		super(message);
		this.operation = operation;
	}

	public PhotoStorageException(Operation operation, String message, Throwable cause) {
		super(message, cause);
		this.operation = operation;
	}

	public Operation operation() {
		return operation;
	}
}
