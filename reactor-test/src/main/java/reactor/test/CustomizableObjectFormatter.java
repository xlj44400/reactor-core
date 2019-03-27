/*
 * Copyright (c) 2011-2019 Pivotal Software Inc, All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package reactor.test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import reactor.core.publisher.Signal;
import reactor.util.annotation.Nullable;

/**
 * A class that holds custom class-specific converters of object to {@link String}, as
 * well as an optional catch-all {@link Object}-to-{@link String} converter. It can be
 * applied as a {@link Function}. A static utility method {@link #convertVarArgs(Function, Object...)}
 * can be used to apply it to varargs.
 * <p>
 * By default it treats {@link Signal} objects particularly: if the signal
 * is an onNext, the <i>value</i> is converted. Then whatever the type, the {@link Signal}
 * itself is optionally converted using a {@link Signal}-specific converter (if there is one).
 * It doesn't go through the {@link #setCatchAll(Function) catch all} converter though.
 * This behavior can be deactivated (thus converting the {@link Signal} like any other object)
 * by calling {@link #setUnwrap(boolean) setUnwrap(false)}.
 *
 * @author Simon Baslé
 */
final class CustomizableObjectFormatter implements Function<Object, String> {

	static CustomizableObjectFormatter simple(Function<Object, String> catchAll) {
		CustomizableObjectFormatter simple = new CustomizableObjectFormatter();
		simple.setCatchAll(catchAll);
		return simple;
	}

	private final Map<Class<?>, Function<Object, String>> converters = new LinkedHashMap<>();
	@Nullable
	private       Function<Object, String>                catchAll;
	private       boolean                                 unwrap = true;

	void setUnwrap(boolean unwrap) {
		this.unwrap = unwrap;
	}

	void setCatchAll(Function<Object, String> catchAll) {
		this.catchAll = catchAll;
	}

	<T> void setConverter(Class<T> clazz, Function<T, String> converter) {
		//noinspection unchecked
		this.converters.put(clazz, (Function<Object, String>) converter);
	}

	@Override
	public String apply(@Nullable Object o) {
		if (unwrap && o instanceof Signal) {
			return unwrapSignal((Signal) o);
		}

		return applyConverters(o);
	}

	//protects against deeply nested Signals
	private String applyConverters(@Nullable Object o) {
		if (o == null) {
			return "null";
		}
		for (Map.Entry<Class<?>, Function<Object, String>> entry : converters.entrySet()) {
			Class<?> c = entry.getKey();
			if (c.isInstance(o)) {
				return entry.getValue().apply(o);
			}
		}
		if (catchAll != null) {
			return catchAll.apply(o);
		}
		return String.valueOf(o);
	}

	//Signal onNext must have its value converted
	//Signal can then be passed to specific converters BUT NOT catch all converter
	private String unwrapSignal(Signal sig) {
		if (sig.isOnNext()) {
			Object val = sig.get();
			String stringRepresentation = val == null ? "null" : this.applyConverters(val);
			sig = Signal.next(stringRepresentation);
		}


		for (Map.Entry<Class<?>, Function<Object, String>> entry : converters.entrySet()) {
			Class<?> c = entry.getKey();
			if (c.isInstance(sig)) {
				return entry.getValue().apply(sig);
			}
		}
		return String.valueOf(sig);
	}

	/**
	 * Convert the whole vararg array by applying this formatter to each element in it.
	 * @param args the vararg to format
	 * @return a formatted array usable in replacement of the vararg
	 */
	@Nullable
	static final Object[] convertVarArgs(Function<Object, String> converter, @Nullable Object... args) {
		if (args == null) return null;
		Object[] converted = new Object[args.length];
		for (int i = 0; i < args.length; i++) {
			Object arg = args[i];
			converted[i] = converter.apply(arg);
		}
		return converted;
	}
}
