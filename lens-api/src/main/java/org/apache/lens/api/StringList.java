/*
 * 
 */
package org.apache.lens.api;

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

import javax.xml.bind.annotation.XmlRootElement;
import java.util.List;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The Class StringList.
 */
@XmlRootElement
/**
 * Instantiates a new string list.
 *
 * @param elements
 *          the elements
 */
@AllArgsConstructor
/**
 * Instantiates a new string list.
 */
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StringList {

  /** The elements. */
  @Getter
  @Setter
  private List<String> elements;
}