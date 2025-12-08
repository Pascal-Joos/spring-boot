/*
 * Copyright 2012-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.context.properties.bind;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.Arrays;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.KotlinDetector;
import org.springframework.core.annotation.MergedAnnotations;
import javax.annotation.Nullable;
import org.springframework.util.Assert;

/**
 * Default {@link BindConstructorProvider} implementation.
 *
 * @author Madhura Bhave
 * @author Phillip Webb
 */
class DefaultBindConstructorProvider implements BindConstructorProvider {

	@Nullable
	@Override
	public Constructor<?> getBindConstructor(Bindable<?> bindable, boolean isNestedConstructorBinding) {
		return getBindConstructor(bindable.getType().resolve(), isNestedConstructorBinding);
	}

	@Nullable
	@Override
	public Constructor<?> getBindConstructor(Class<?> type, boolean isNestedConstructorBinding) {
		if (type == null) {
			return null;
		}
		Constructors constructors = Constructors.getConstructors(type);
		if (constructors.getBind() != null || isNestedConstructorBinding) {
			Assert.state(!constructors.hasAutowired(),
					() -> type.getName() + " declares @ConstructorBinding and @Autowired constructor");
		}
		return constructors.getBind();
	}

	/**
	 * Data holder for autowired and bind constructors.
	 */
	static final class Constructors {

		private final boolean hasAutowired;

		@Nullable
		private final Constructor<?> bind;

		private Constructors(boolean hasAutowired, @Nullable Constructor<?> bind) {
			this.hasAutowired = hasAutowired;
			this.bind = bind;
		}

		boolean hasAutowired() {
			return this.hasAutowired;
		}

		@Nullable
		Constructor<?> getBind() {
			return this.bind;
		}

		static Constructors getConstructors(Class<?> type) {
			if (!isKotlinType(type)) {
				return getJavaConstructors(type);
			}
			return getKotlinConstructors(type);
		}

		private static Constructors getJavaConstructors(Class<?> type) {
			Constructor<?>[] declaredConstructors = type.getDeclaredConstructors();
			Constructor<?>[] candidates = Arrays.stream(declaredConstructors)
					.filter((candidate) -> !candidate.isSynthetic()).toArray(Constructor<?>[]::new);
			MergedAnnotations[] mergedAnnotations = getAnnotations(candidates);
			boolean hasAutowired = isAutowiredPresent(mergedAnnotations);
			Constructor<?> bind = getConstructorBindingAnnotated(type, candidates, mergedAnnotations);
			if (bind == null && !hasAutowired) {
				bind = deduceBindConstructor(type, candidates);
			}
			return new Constructors(hasAutowired, bind);
		}

		private static Constructors getKotlinConstructors(Class<?> type) {
			Constructor<?> bind = deduceKotlinBindConstructor(type);
			return new Constructors(false, bind);
		}

		private static MergedAnnotations[] getAnnotations(Constructor<?>[] candidates) {
			MergedAnnotations[] candidateAnnotations = new MergedAnnotations[candidates.length];
			for (int i = 0; i < candidates.length; i++) {
				candidateAnnotations[i] = MergedAnnotations.from(candidates[i]);
			}
			return candidateAnnotations;
		}

		private static boolean isAutowiredPresent(MergedAnnotations[] candidateAnnotations) {
			for (MergedAnnotations annotations : candidateAnnotations) {
				if (annotations.isPresent(Autowired.class)) {
					return true;
				}
			}
			return false;
		}

		@Nullable
			private static Constructor<?> getConstructorBindingAnnotated(Class<?> type, Constructor<?>[] candidates,
				MergedAnnotations[] mergedAnnotations) {
			Constructor<?> result = null;
			for (int i = 0; i < candidates.length; i++) {
				if (mergedAnnotations[i].isPresent(ConstructorBinding.class)) {
					Assert.state(candidates[i].getParameterCount() > 0,
							() -> type.getName() + " declares @ConstructorBinding on a no-args constructor");
					Assert.state(result == null,
							() -> type.getName() + " has more than one @ConstructorBinding constructor");
					result = candidates[i];
				}
			}
			return result;

		}

		@Nullable
		private static Constructor<?> deduceBindConstructor(Class<?> type, Constructor<?>[] candidates) {
			if (candidates.length == 1 && candidates[0].getParameterCount() > 0) {
				if (type.isMemberClass() && Modifier.isPrivate(candidates[0].getModifiers())) {
					return null;
				}
				return candidates[0];
			}
			Constructor<?> result = null;
			for (Constructor<?> candidate : candidates) {
				if (!Modifier.isPrivate(candidate.getModifiers())) {
					if (result != null) {
						return null;
					}
					result = candidate;
				}
			}
			return (result != null && result.getParameterCount() > 0) ? result : null;
		}

		private static boolean isKotlinType(Class<?> type) {
			return KotlinDetector.isKotlinPresent() && KotlinDetector.isKotlinType(type);
		}

		@Nullable
		private static Constructor<?> deduceKotlinBindConstructor(Class<?> type) {
			Constructor<?> primaryConstructor = BeanUtils.findPrimaryConstructor(type);
			if (primaryConstructor != null && primaryConstructor.getParameterCount() > 0) {
				return primaryConstructor;
			}
			return null;
		}

	}

}
