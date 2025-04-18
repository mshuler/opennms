/*
 * Licensed to The OpenNMS Group, Inc (TOG) under one or more
 * contributor license agreements.  See the LICENSE.md file
 * distributed with this work for additional information
 * regarding copyright ownership.
 *
 * TOG licenses this file to You under the GNU Affero General
 * Public License Version 3 (the "License") or (at your option)
 * any later version.  You may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at:
 *
 *      https://www.gnu.org/licenses/agpl-3.0.txt
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the
 * License.
 */
// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: twin-message.proto

package org.opennms.core.ipc.twin.model;

public interface TwinRequestProtoOrBuilder extends
    // @@protoc_insertion_point(interface_extends:TwinRequestProto)
    com.google.protobuf.MessageOrBuilder {

  /**
   * <code>string consumer_key = 1;</code>
   * @return The consumerKey.
   */
  java.lang.String getConsumerKey();
  /**
   * <code>string consumer_key = 1;</code>
   * @return The bytes for consumerKey.
   */
  com.google.protobuf.ByteString
      getConsumerKeyBytes();

  /**
   * <code>string system_id = 2;</code>
   * @return The systemId.
   */
  java.lang.String getSystemId();
  /**
   * <code>string system_id = 2;</code>
   * @return The bytes for systemId.
   */
  com.google.protobuf.ByteString
      getSystemIdBytes();

  /**
   * <code>string location = 3;</code>
   * @return The location.
   */
  java.lang.String getLocation();
  /**
   * <code>string location = 3;</code>
   * @return The bytes for location.
   */
  com.google.protobuf.ByteString
      getLocationBytes();

  /**
   * <code>map&lt;string, string&gt; tracing_info = 4;</code>
   */
  int getTracingInfoCount();
  /**
   * <code>map&lt;string, string&gt; tracing_info = 4;</code>
   */
  boolean containsTracingInfo(
      java.lang.String key);
  /**
   * Use {@link #getTracingInfoMap()} instead.
   */
  @java.lang.Deprecated
  java.util.Map<java.lang.String, java.lang.String>
  getTracingInfo();
  /**
   * <code>map&lt;string, string&gt; tracing_info = 4;</code>
   */
  java.util.Map<java.lang.String, java.lang.String>
  getTracingInfoMap();
  /**
   * <code>map&lt;string, string&gt; tracing_info = 4;</code>
   */
  /* nullable */
java.lang.String getTracingInfoOrDefault(
      java.lang.String key,
      /* nullable */
java.lang.String defaultValue);
  /**
   * <code>map&lt;string, string&gt; tracing_info = 4;</code>
   */
  java.lang.String getTracingInfoOrThrow(
      java.lang.String key);
}
