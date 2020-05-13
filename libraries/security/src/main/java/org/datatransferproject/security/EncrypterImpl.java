/*
 * Copyright 2018 The Data Transfer Project Authors.
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
package org.datatransferproject.security;

import com.google.common.base.Charsets;
import com.google.common.io.BaseEncoding;
import org.datatransferproject.api.launcher.Monitor;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import java.security.InvalidKeyException;
import java.security.InvalidAlgorithmParameterException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import static java.lang.String.format;

/**
 * Provides AES and RSA-based encryption implementations. See {@link EncrypterFactory} to create.
 */
final class EncrypterImpl implements Encrypter {
  private final Key key;
  private final CryptoTransformation transformation;
  private final Monitor monitor;

  EncrypterImpl(CryptoTransformation transformation, Key key, Monitor monitor) {
    this.key = key;
    this.transformation = transformation;
    this.monitor = monitor;
  }

  @Override
  public String encrypt(String data) {
    try {
      Cipher cipher;
      switch (transformation) {
        case AES_CBC_NOPADDING:
          cipher = Cipher.getInstance("AES/CBC/NoPadding");
          cipher.init(Cipher.ENCRYPT_MODE, key, generateIv(cipher));
          break;
        case RSA_ECB_PKCS1:
          cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
          cipher.init(Cipher.ENCRYPT_MODE, key);        
          break;
        default:
          throw new AssertionError("How could this happen...");
      }
      // we use a salt the size of the first block
      // so that we don't need to know IV for AES/CBC
      byte[] salt = new byte[cipher.getBlockSize()];
      SecureRandom random = SecureRandom.getInstance("SHA1PRNG");
      random.nextBytes(salt);
      cipher.update(salt);
      byte[] encrypted = cipher.doFinal(data.getBytes(Charsets.UTF_8));
      return BaseEncoding.base64Url().encode(encrypted);
    } catch (BadPaddingException
        | IllegalBlockSizeException
        | InvalidAlgorithmParameterException
        | InvalidKeyException
        | NoSuchAlgorithmException
        | NoSuchPaddingException e) {
      monitor.severe(() -> format("Exception encrypting data, length: %s", data.length()), e);
      throw new RuntimeException(e);
    }
  }

  private static final IvParameterSpec generateIv(Cipher cipher) throws NoSuchAlgorithmException {
    SecureRandom randomSecureRandom = SecureRandom.getInstance("SHA1PRNG");
    byte[] iv = new byte[cipher.getBlockSize()];
    randomSecureRandom.nextBytes(iv);
    return new IvParameterSpec(iv);
  }
}
