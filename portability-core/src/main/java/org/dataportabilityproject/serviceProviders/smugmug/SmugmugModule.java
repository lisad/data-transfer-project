/*
 * Copyright 2017 The Data Transfer Project Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.dataportabilityproject.serviceProviders.smugmug;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.MapBinder;
import org.dataportabilityproject.shared.ServiceProvider;

public class SmugmugModule extends AbstractModule {

  @Override
  protected void configure() {
    MapBinder<String, ServiceProvider> mapbinder =
        MapBinder.newMapBinder(binder(), String.class, ServiceProvider.class);
    mapbinder.addBinding(SmugMugServiceProvider.class.getName()).to(SmugMugServiceProvider.class);
  }
}
