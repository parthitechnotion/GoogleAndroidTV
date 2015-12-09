/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer.upstream.crypto;

import com.google.android.exoplayer.upstream.DataSink;
import com.google.android.exoplayer.upstream.DataSpec;
import com.google.android.exoplayer.util.ClosedSource;

import java.io.IOException;

import javax.crypto.Cipher;

/**
 * A wrapping {@link DataSink} that encrypts the data being consumed.
 */
@ClosedSource(reason = "Crypto package not open sourced")
public class AesCipherDataSink implements DataSink {

  private final DataSink wrappedDataSink;
  private final byte[] secretKey;
  private final byte[] scratch;

  private AesFlushingCipher cipher;

  /**
   * Create an instance whose {@code write} methods have the side effect of overwriting the input
   * {@code data}.
   *
   * Use this constructor for maximum efficiency in the case that there is no requirement for the
   * input data arrays to remain unchanged.
   */
  public AesCipherDataSink(byte[] secretKey, DataSink wrappedDataSink) {
    this(secretKey, null, wrappedDataSink);
  }

  /**
   * Create an instance whose {@code write} methods are free of side effects.
   *
   * Use this constructor when the input data arrays are required to remain unchanged.
   */
  public AesCipherDataSink(byte[] secretKey, byte[] scratch, DataSink wrappedDataSink) {
    this.wrappedDataSink = wrappedDataSink;
    this.secretKey = secretKey;
    this.scratch = scratch;
  }

  @Override
  public DataSink open(DataSpec dataSpec) throws IOException {
    wrappedDataSink.open(dataSpec);
    long nonce = CryptoUtil.getFNV64Hash(dataSpec.key);
    cipher = new AesFlushingCipher(Cipher.ENCRYPT_MODE, secretKey, nonce,
        dataSpec.absoluteStreamPosition);
    return this;
  }

  @Override
  public void write(byte[] data, int offset, int length) throws IOException {
    if (scratch == null) {
      // In-place mode. Writes over the input data.
      cipher.updateInPlace(data, offset, length);
      wrappedDataSink.write(data, offset, length);
    } else {
      // Use scratch space. The original data remains intact.
      int bytesProcessed = 0;
      while (bytesProcessed < length) {
        int bytesToProcess = Math.min(length - bytesProcessed, scratch.length);
        cipher.update(data, offset + bytesProcessed, bytesToProcess, scratch, 0);
        wrappedDataSink.write(scratch, 0, bytesToProcess);
        bytesProcessed += bytesToProcess;
      }
    }
  }

  @Override
  public void close() throws IOException {
    cipher = null;
    wrappedDataSink.close();
  }

}
