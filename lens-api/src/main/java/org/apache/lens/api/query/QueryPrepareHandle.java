/*
 * 
 */
package org.apache.lens.api.query;

/*
 * #%L
 * Lens API
 * %%
 * Copyright (C) 2014 Apache Software Foundation
 * %%
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
 * #L%
 */

import java.util.UUID;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/**
 * The Class QueryPrepareHandle.
 */
@XmlRootElement
/**
 * Instantiates a new query prepare handle.
 *
 * @param prepareHandleId
 *          the prepare handle id
 */
@AllArgsConstructor
/**
 * Instantiates a new query prepare handle.
 */
@NoArgsConstructor(access = AccessLevel.PROTECTED)
/*
 * (non-Javadoc)
 *
 * @see java.lang.Object#hashCode()
 */
@EqualsAndHashCode(callSuper = false)
public class QueryPrepareHandle extends QuerySubmitResult {

  /** The prepare handle id. */
  @XmlElement
  @Getter
  private UUID prepareHandleId;

  /**
   * From string.
   *
   * @param handle
   *          the handle
   * @return the query prepare handle
   */
  public static QueryPrepareHandle fromString(String handle) {
    return new QueryPrepareHandle(UUID.fromString(handle));
  }

  /*
   * (non-Javadoc)
   *
   * @see java.lang.Object#toString()
   */
  @Override
  public String toString() {
    return prepareHandleId.toString();
  }
}