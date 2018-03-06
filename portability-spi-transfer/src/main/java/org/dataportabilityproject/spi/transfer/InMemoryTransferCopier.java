/*
 * Copyright 2018 The Data-Portability Project Authors.
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
package org.dataportabilityproject.spi.transfer;

import java.io.IOException;
import java.util.UUID;
import org.dataportabilityproject.spi.transfer.provider.Exporter;
import org.dataportabilityproject.spi.transfer.provider.Importer;
import org.dataportabilityproject.types.transfer.auth.AuthData;

/**
 * In-memory Copier interface
 */
public interface InMemoryTransferCopier {
  /* Copies the provided dataType from exportService to importService */
  void copy(
      Exporter exporter,
      Importer importer,
      AuthData exportAuthData,
      AuthData importAuthData,
      UUID jobId) throws IOException;
}
