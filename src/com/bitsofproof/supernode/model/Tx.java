/*
 * Copyright 2012 Tamas Blummer tamas@bitsofproof.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.bitsofproof.supernode.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.Table;

import com.bitsofproof.supernode.core.ByteUtils;
import com.bitsofproof.supernode.core.Script;
import com.bitsofproof.supernode.core.ValidationException;
import com.bitsofproof.supernode.core.WireFormat;

@Entity
@Table (name = "tx")
public class Tx implements Serializable
{
	private static final long serialVersionUID = 1L;

	public static final long COIN = 100000000;
	public static final long MAX_MONEY = 2099999997690000L;

	private void checkMoneyRange (long n) throws ValidationException
	{
		if ( n < 0 || n > MAX_MONEY )
		{
			throw new ValidationException ("outside money range");
		}
	}

	@Id
	@GeneratedValue
	private Long id;

	private long version;

	private long lockTime;

	// this is not unique on the chain see http://r6.ca/blog/20120206T005236Z.html
	@Column (length = 64, nullable = false)
	private String hash;

	@OneToMany (fetch = FetchType.LAZY, cascade = CascadeType.ALL)
	private List<TxIn> inputs;

	@OneToMany (fetch = FetchType.LAZY, cascade = CascadeType.ALL)
	private List<TxOut> outputs;

	public Long getId ()
	{
		return id;
	}

	public void setId (Long id)
	{
		this.id = id;
	}

	public long getVersion ()
	{
		return version;
	}

	public void setVersion (long version)
	{
		this.version = version;
	}

	public long getLockTime ()
	{
		return lockTime;
	}

	public void setLockTime (long lockTime)
	{
		this.lockTime = lockTime;
	}

	public List<TxIn> getInputs ()
	{
		return inputs;
	}

	public void setInputs (List<TxIn> inputs)
	{
		this.inputs = inputs;
	}

	public List<TxOut> getOutputs ()
	{
		return outputs;
	}

	public void setOutputs (List<TxOut> outputs)
	{
		this.outputs = outputs;
	}

	public String getHash ()
	{
		if ( hash == null )
		{
			WireFormat.Writer writer = new WireFormat.Writer ();
			toWire (writer);
			WireFormat.Reader reader = new WireFormat.Reader (writer.toByteArray ());
			hash = reader.hash ().toString ();
		}
		return hash;
	}

	public void basicValidation () throws ValidationException
	{
		// cheap validations only
		if ( inputs.isEmpty () || outputs.isEmpty () )
		{
			throw new ValidationException ("Input or Output of transaction is empty");
		}

		long sumOut = 0;
		for ( TxOut out : outputs )
		{
			if ( out.getScript ().length > 520 )
			{
				throw new ValidationException ("script too long");
			}
			long n = out.getValue ();
			checkMoneyRange (n);
			sumOut += n;
		}
		checkMoneyRange (sumOut);
		for ( TxIn in : inputs )
		{
			if ( in.getScript ().length > 520 )
			{
				throw new ValidationException ("script too long");
			}
			if ( !Script.isPushOnly (in.getScript ()) )
			{
				throw new ValidationException ("input script should be push only");
			}
		}

	}

	public String toWireDump ()
	{
		WireFormat.Writer writer = new WireFormat.Writer ();
		toWire (writer);
		return ByteUtils.toHex (writer.toByteArray ());
	}

	public static Tx fromWireDump (String s)
	{
		WireFormat.Reader reader = new WireFormat.Reader (ByteUtils.fromHex (s));
		Tx b = new Tx ();
		b.fromWire (reader);
		return b;
	}

	public String toJSON ()
	{
		StringBuffer b = new StringBuffer ();
		b.append ("{");
		b.append ("\"hash\":\"" + getHash () + "\",");
		b.append ("\"version\":" + String.valueOf (version) + ",");
		b.append ("\"inputs\":[");
		boolean first = true;
		for ( TxIn input : inputs )
		{
			if ( !first )
			{
				b.append (",");
			}
			first = false;
			b.append (input.toJSON ());
		}
		b.append ("],");
		b.append ("\"outputs\":[");
		first = true;
		for ( TxOut output : outputs )
		{
			if ( !first )
			{
				b.append (",");
			}
			first = false;
			b.append (output.toJSON ());
		}
		b.append ("],");
		b.append ("\"lockTime\":" + String.valueOf (lockTime));
		b.append ("}");
		return b.toString ();
	}

	public void toWire (WireFormat.Writer writer)
	{
		writer.writeUint32 (version);
		if ( inputs != null )
		{
			writer.writeVarInt (inputs.size ());
			for ( TxIn input : inputs )
			{
				input.toWire (writer);
			}
		}
		else
		{
			writer.writeVarInt (0);
		}

		if ( outputs != null )
		{
			writer.writeVarInt (outputs.size ());
			for ( TxOut output : outputs )
			{
				output.toWire (writer);
			}
		}
		else
		{
			writer.writeVarInt (0);
		}

		writer.writeUint32 (lockTime);
	}

	public void fromWire (WireFormat.Reader reader)
	{
		int cursor = reader.getCursor ();

		version = reader.readUint32 ();
		long nin = reader.readVarInt ();
		if ( nin > 0 )
		{
			inputs = new ArrayList<TxIn> ();
			for ( int i = 0; i < nin; ++i )
			{
				TxIn input = new TxIn ();
				input.fromWire (reader);
				input.setTransaction (this);
				inputs.add (input);
			}
		}
		else
		{
			inputs = null;
		}

		long nout = reader.readVarInt ();
		if ( nout > 0 )
		{
			outputs = new ArrayList<TxOut> ();
			for ( int i = 0; i < nout; ++i )
			{
				TxOut output = new TxOut ();
				output.fromWire (reader);
				output.setTransaction (this);
				output.setIx (i);
				outputs.add (output);
			}
		}
		else
		{
			outputs = null;
		}

		lockTime = reader.readUint32 ();

		hash = reader.hash (cursor, reader.getCursor () - cursor).toString ();
	}

	public Tx flatCopy ()
	{
		Tx c = new Tx ();

		c.hash = hash;
		c.lockTime = lockTime;
		c.version = version;
		c.inputs = new ArrayList<TxIn> ();
		for ( TxIn in : inputs )
		{
			c.inputs.add (in.flatCopy (c));
		}
		c.outputs = new ArrayList<TxOut> ();
		for ( TxOut out : outputs )
		{
			c.outputs.add (out.flatCopy (c));
		}

		return c;
	}
}
