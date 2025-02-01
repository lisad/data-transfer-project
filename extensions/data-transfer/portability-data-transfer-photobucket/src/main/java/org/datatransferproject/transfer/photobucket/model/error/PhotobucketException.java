package org.datatransferproject.transfer.photobucket.model.error;

import org.datatransferproject.spi.transfer.types.CopyExceptionWithFailureReason;
import org.jetbrains.annotations.NotNull;

public class PhotobucketException extends CopyExceptionWithFailureReason {
    public PhotobucketException(String message, Throwable cause) {
        super(message, cause);
    }

    @NotNull
    @Override
    public String getFailureReason() {
        return this.getCause().getClass().getName();
    }
}
