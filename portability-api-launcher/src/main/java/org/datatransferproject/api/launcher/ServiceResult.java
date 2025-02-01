/*
 * Copyright 2019 The Data Transfer Project Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.datatransferproject.api.launcher;
public class ServiceResult{
    private java.lang.String service;

    public java.lang.String getService(){
        return service;
    }

    public void setService(java.lang.String service){
        this.service=service;
    }

    private boolean success;

    public boolean getSuccess(){
        return success;
    }

    public void setSuccess(boolean success){
        this.success=success;
    }

    private java.time.Duration duration;

    public java.time.Duration getDuration(){
        return duration;
    }

    public void setDuration(java.time.Duration duration){
        this.duration=duration;
    }

    public ServiceResult(java.lang.String service,boolean success,java.time.Duration duration){
        this.service=service;
        this.success=success;
        this.duration=duration;
    }
}

