/**
 * Licensed to Apereo under one or more contributor license
 * agreements. See the NOTICE file distributed with this work
 * for additional information regarding copyright ownership.
 * Apereo licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License.  You may obtain a
 * copy of the License at the following location:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.jasig.ssp.model.reference;

import org.jasig.ssp.model.Auditable;
import javax.persistence.Entity;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import java.util.UUID;

@Entity
@Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
public class CareerDecisionStatus extends AbstractReference implements Auditable {

	/**
	 *
	 */
	private static final long serialVersionUID = -4613086327012079359L;
	private String code;

	/**
	 * Constructor
	 */
	public CareerDecisionStatus () {
		super();
	}

	/**
	 * Constructor
	 *
	 * @param id
	 *            Identifier; required
	 */

	public CareerDecisionStatus (final UUID id) {
		super(id);
	}

	/**
	 * Constructor
	 *
	 * @param id
	 *            Identifier; required
	 * @param name
	 *            Name; required; max 100 characters
	 */

	public CareerDecisionStatus (final UUID id, final String name) {
		super(id, name);
	}

	/**
	 * Constructor
	 *
	 * @param id
	 *            Identifier; required
	 * @param name
	 *            Name; required; max 100 characters
	 * @param description
	 *            Description; max 150 characters
	 */
	public CareerDecisionStatus (final UUID id, final String name, final String description) {
		super(id, name, description);
	}
	
	/**
	 * @return the career status code
	 */
	public String getCode() {
		return code;
	}
	
	/**
	 * @param career status external ref code
	 * 				the career status code to set
	 */
	public void setCode(final String code) {
		this.code = code;
	}

	@Override
	protected int hashPrime() {
		return 111;
	}
	
	@Override
	public int hashCode() { // NOPMD by jon.adams
		return hashPrime() * super.hashCode()
				* hashField("code", code);
	}	
}
