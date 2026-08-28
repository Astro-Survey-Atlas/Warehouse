/*
 * Copyright 2026 Astro Survey Atlas contributors.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.zhejianglab.astro.atlas.core;

/** Names of environment variables or mounted files, never credential values. */
public record CredentialRef(
    String accessKeyEnv,
    String secretKeyEnv,
    String usernameEnv,
    String passwordEnv,
    String accessKeyFile,
    String secretKeyFile,
    String usernameFile,
    String passwordFile) {
  public static CredentialRef none() {
    return new CredentialRef(null, null, null, null, null, null, null, null);
  }

  public void validate() {
    checkReference(accessKeyEnv, "accessKeyEnv");
    checkReference(secretKeyEnv, "secretKeyEnv");
    checkReference(usernameEnv, "usernameEnv");
    checkReference(passwordEnv, "passwordEnv");
    checkReference(accessKeyFile, "accessKeyFile");
    checkReference(secretKeyFile, "secretKeyFile");
    checkReference(usernameFile, "usernameFile");
    checkReference(passwordFile, "passwordFile");
  }

  private static void checkReference(String value, String name) {
    if (value != null && value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
  }
}
