package swp.util;


import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Created by parttimenerd on 11.10.16.
 */
public class ReflectionUtils {

	public static class RUError extends Error {
		public final Object caller;

		public RUError(Object caller, String msg){
			super((caller == null ? "" : (caller.toString() + ": ")) + msg);
			this.caller = caller;
		}
	}

	public static class RUFunctionError extends RUError {
		public final RUFunction calledFunction;
		public final Object[] args;

		public RUFunctionError(String msg, Object caller, RUFunction calledFunction, Object[] args){
			super(caller, calledFunction.toString() + "(" + Arrays.asList(args).stream().map(Object::toString)
					.collect(Collectors.joining(",")) + "): " + msg);
			this.calledFunction = calledFunction;
			this.args = args;
		}
	}

	public static class RUWrappedFunctionError extends RUFunctionError {
		public final Throwable wrappedError;


		public RUWrappedFunctionError(Throwable wrappedError, Object caller, RUFunction calledFunction, Object[] args) {
			super(wrappedError.getMessage(), caller, calledFunction, args);
			this.wrappedError = wrappedError;
			setStackTrace(wrappedError.getStackTrace());
		}
	}

	public static class RUWrappedError extends RUError {
		public final Throwable wrappedError;

		public RUWrappedError(Throwable wrappedError, Object caller, String msg) {
			super(caller, (msg != null ? (msg + ": ") : "") + wrappedError.getMessage());
			this.wrappedError = wrappedError;
		}
	}

	public static class RUNoSuchFieldError extends RUError {
		public final Object base;
		public final String field;

		public RUNoSuchFieldError(Object caller, Object base, String field){
			super(caller, String.format("No such field %s in object %s", field, base.toString()));
			this.field = field;
			this.base = base;
		}
	}

	public static abstract class RUFunction {
		protected final String name;
		public final int minArgs;
		public final Object baseObj;
		public final boolean hasBaseObject;

		public RUFunction(String name, int minArgs, Object baseObj, boolean hasBaseObject) {
			this.name = name;
			this.minArgs = minArgs;
			this.baseObj = baseObj;
			this.hasBaseObject = hasBaseObject;
		}

		public void checkArguments(Object caller, Object[] args){

		}

		public Object apply(Object caller, Object[] args){
			checkArguments(caller, args);
			return applyImpl(caller, args);
		}

		public abstract Object applyImpl(Object caller, Object[] args);

		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("Function[");
			List<String> strs = new ArrayList<>();
			if (hasName()){
				strs.add("\"" + name + "\"");
			}
			if (hasBaseObject){
				strs.add("base=" + baseObj);
			}
			strs.add("min args=" + minArgs);
			builder.append(String.join(", ", strs)).append("]");
			return builder.toString();
		}

		public boolean hasName(){
			return name != null;
		}

		public String getName(){
			if (hasName()){
				return name;
			}
			return "anonymous";
		}
	}

	public static class RUClosureFunction extends RUFunction {

		protected final CheckedFunction<Object[], Object> action;

		public RUClosureFunction(String name, int minArgs, Object baseObj, boolean hasBaseObject,
		                         CheckedFunction<Object[], Object> action) {
			super(name, minArgs, baseObj, hasBaseObject);
			this.action = action;
		}

		@Override
		public Object applyImpl(Object caller, Object[] args) {
			Object ret = null;
			try {
				ret = action.apply(args);
			} catch (RUWrappedFunctionError error){
				throw new RUWrappedFunctionError(error.wrappedError, caller, this, args);
			} catch (RUFunctionError error){
				throw error;
			} catch (Throwable error){
				error.printStackTrace();
				throw new RUWrappedFunctionError(error, caller, this, args);
			}
			return ret;
		}
	}

	public static class RUField {
		public final Object baseObject;
		public final String name;
		private final CheckedProducer<Object> getter;
		private final CheckedConsumer<Object> setter;

		public RUField(Object baseObject, String name, CheckedProducer<Object> getter, CheckedConsumer<Object> setter) {
			this.baseObject = baseObject;
			this.name = name;
			this.getter = getter;
			this.setter = setter;
		}

		public Object get(Object caller){
			try {
				return getter.apply();
			} catch (Throwable throwable) {
				throw new RUWrappedFunctionError(throwable, baseObject, null, new Object[]{(String)name});
			}
		}

		public Object set(Object caller, Object value){
			if (setter == null){
				throw new RUError(caller, "No setter for " + toString());
			}
			try {
				setter.consume(value);
				return value;
			} catch (Throwable throwable) {
				throw new RUWrappedFunctionError(throwable, baseObject, null, new Object[]{(String)name});
			}
		}

		@Override
		public String toString() {
			return baseObject + "." + name;
		}
	}

	public static RUField getRUField(Object caller, Object base, Object name){
		String _name = (String) name;
		if (hasFunction(base, _name)){
			return getFunctionField(caller, base, _name);
		}
		return getField(caller, base, _name);
	}

	private static RUField getField(Object caller, Object base, String field){
		RUField f;
		if ((f = getStaticMiscField(caller, base, field)) != null){
			return f;
		}
		Object value;
		CheckedConsumer<Object> setter;
		try {
			Field _field;
			if (base instanceof Class) {
				Class _base = (Class) base;
				_field = _base.getField(field);
				value = _field.get(base);
			} else {
				_field = base.getClass().getField(field);
				value = _field.get(base);
			}
			setter = arg -> _field.set(base, arg);
		} catch (NoSuchFieldException | SecurityException | IllegalAccessException e){
			throw new RUNoSuchFieldError(caller, base, field);
		}
		return new RUField(base, field, () -> value, setter);
	}

	private static RUField getStaticMiscField(Object caller, Object base, String field){
		Object value;
		CheckedConsumer<Object> setter = null;
		switch (field){
			case "methods":
				value = getAccessibleMethodNames(base instanceof Class ? (Class)base : base.getClass());
				break;
			default:
				return null;
		}
		return new RUField(base, field, () -> value, setter == null ? newValue -> {
			throw new RUError(caller, String.format("Can't set the value of the field %s.%s", base, field));
		} : setter);
	}

	private static boolean hasFunction(Object base, String name){
		if (base instanceof Class) {
			if (Objects.equals(name, "new")){
				return true;
			}
			Class _base = (Class) base;
			return getSuitedMethods(_base.getMethods(), name).size() > 0;
		} else {
			return getSuitedMethods(base.getClass().getMethods(), name).size() > 0;
		}
	}

	private static RUField getFunctionField(Object caller, Object base, String name){
		RUFunction function = getFunction(base, name);
		return new RUField(base, name, () -> function, newValue -> {
			throw new RUError(caller, String.format("Can't set the value of the function field %s.%s", base, name));
		});
	}

	private static RUFunction getFunction(Object base, String name){
		int minArgs = 0;
		CheckedFunction<Object[], Object> impl;
		if (base instanceof Class){
			Class _base = (Class)base;
			switch (name){
				case "new":
					minArgs = Arrays.stream(_base.getConstructors()).map(Constructor::getParameterCount)
							.min(Integer::compare).get();
					impl = args -> {
						return _base.getConstructor(getTypesOfObjectArr(args)).newInstance(args);
					};
					break;
				default:
					//List<Method> suitedMethods = getSuitedMethods(base.getClass().getDeclaredMethods(), name);
					//minArgs = 0;//getMinArgsOfMethods(suitedMethods);
					impl = args -> {
						return _base.getMethod(name, getTypesOfObjectArr(args)).invoke(base, args);
					};
			}
		} else {
			//List<Method> suitedMethods = getSuitedMethods(base.getClass().getMethods(), name);
			minArgs = 0; //getMinArgsOfMethods(suitedMethods);
			switch (name) {
				default:
					impl = args -> {
						return base.getClass().getMethod(name, getTypesOfObjectArr(args)).invoke(base, args);
					};
			}
		}
		return new RUClosureFunction(name, minArgs, base, true, impl);
	}

	/**
	 * source: http://stackoverflow.com/questions/1857775/getting-a-list-of-accessible-methods-for-a-given-class-via-reflection
	 */
	private static List<Method> getAccessibleMethods(Class clazz) {
		List<Method> result = new ArrayList<Method>();
		while (clazz != null) {
			for (Method method : clazz.getDeclaredMethods()) {
				int modifiers = method.getModifiers();
				if (Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers)) {
					result.add(method);
				}
			}
			clazz = clazz.getSuperclass();
		}
		return result;
	}

	private static List<String> getAccessibleMethodNames(Class clazz){
		return getAccessibleMethods(clazz).stream().map(Method::getName).collect(Collectors.toList());
	}

	public static Object call(Object caller, Object obj, Object[] args){
		if (obj instanceof RUFunction){
			return ((RUFunction)obj).apply(caller, args);
		} else if (obj instanceof RUField){
			return call(caller, ((RUField)obj).get(caller), args);
		}
		throw new RUError(caller, obj.toString() + " isn't a valid function");
	}

	private static Class[] getTypesOfObjectArr(Object[] objects){
		Class[] types = new Class[objects.length];
		for (int i = 0; i < objects.length; i++) {
			types[i] = objects[i].getClass();
		}
		return types;
	}

	public static Method getBestSuitedMethod(List<Method> methods, Object[] args){
		return methods.get(0);
	}

	private static List<Method> getSuitedMethods(Method[] methods, String name){
		return Arrays.stream(methods).filter(method -> method.getName().equals(name)).collect(Collectors.toList());
	}

	private static int getMinArgsOfMethods(List<Method> methods){
		return methods.stream().map(Method::getParameterCount).min(Integer::compare).get();
	}

	public static Object getValue(Object caller, Object obj){
		if (obj instanceof RUField){
			return ((RUField)obj).get(caller);
		}
		return obj;
	}

	public static void main(String[] args) {
		//call(call(null, getRUField(null, getClass(null, "String"), "new"), new Object[]{""}));
		System.out.println(getValue(null, getRUField(null, getClass(null, "String"), "methods")));
	}

	public static Class getClass(Object caller, String className){
		if (className.contains(".")){
			try {
				return Class.forName(className);
			} catch (ClassNotFoundException e) {
				throw new RUWrappedError(e, caller, null);
			}
		}
		for (Package pkg : Package.getPackages()) {
			try {
				return Class.forName(pkg.getName() + "." + className);
			} catch (ClassNotFoundException e) {}
		}
		throw new RUError(caller, "No such class " + className);
	}
}
