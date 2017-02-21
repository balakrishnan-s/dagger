/*
 * Copyright (C) 2016 The Dagger Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dagger.android;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import android.app.Activity;
import android.app.Application;
import android.app.Fragment;
import com.google.common.collect.ImmutableMap;
import org.robolectric.RobolectricTestRunner;
import dagger.android.AndroidInjector.Factory;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;
import org.robolectric.util.FragmentTestUtil;

@Config(manifest = Config.NONE)
@RunWith(RobolectricTestRunner.class)
public final class AndroidInjectionTest {

  // Most positive tests are performed in javatests/dagger/android/support/functional, but
  // Robolectric's support for framework fragments is lacking, so we supplement those tests here:
  public static class InjectableFragment extends Fragment {
    String tag;
  }

  private static DispatchingAndroidInjector<Fragment> dispatchingFragmentInjector(String tag) {
    return new DispatchingAndroidInjector<>(
        ImmutableMap.of(
            InjectableFragment.class,
            () ->
                new Factory<InjectableFragment>() {
                  @Override
                  public AndroidInjector<InjectableFragment> create(InjectableFragment fragment) {
                    return f -> f.tag = tag;
                  }
                }));
  }

  public static class ApplicationInjectsFragment extends Application
      implements HasDispatchingFragmentInjector {
    @Override
    public DispatchingAndroidInjector<Fragment> fragmentInjector() {
      return dispatchingFragmentInjector("injected by app");
    }
  }

  @Config(manifest = Config.NONE, application = ApplicationInjectsFragment.class)
  @Test
  public void fragmentInjectedByApplication() {
    Activity activity = Robolectric.setupActivity(Activity.class);
    InjectableFragment fragment = new InjectableFragment();
    activity.getFragmentManager().beginTransaction().add(fragment, "tag").commit();

    AndroidInjection.inject(fragment);

    assertThat(fragment.tag).isEqualTo("injected by app");
  }

  public static class ActivityInjectsFragment extends Activity
      implements HasDispatchingFragmentInjector {
    @Override
    public DispatchingAndroidInjector<Fragment> fragmentInjector() {
      return dispatchingFragmentInjector("injected by activity");
    }
  }

  @Config(manifest = Config.NONE, application = ApplicationInjectsFragment.class)
  @Test
  public void fragmentInjectedByActivity() {
    ActivityInjectsFragment activity = Robolectric.setupActivity(ActivityInjectsFragment.class);
    InjectableFragment fragment = new InjectableFragment();
    activity.getFragmentManager().beginTransaction().add(fragment, "tag").commit();

    AndroidInjection.inject(fragment);

    assertThat(fragment.tag).isEqualTo("injected by activity");
  }

  public static class ParentFragmentInjectsChildFragment extends Fragment
      implements HasDispatchingFragmentInjector {
    @Override
    public DispatchingAndroidInjector<Fragment> fragmentInjector() {
      return dispatchingFragmentInjector("injected by parent fragment");
    }
  }

  @Config(manifest = Config.NONE, application = ApplicationInjectsFragment.class)
  @Test
  public void fragmentInjectedByParentFragment() {
    ActivityInjectsFragment activity = Robolectric.setupActivity(ActivityInjectsFragment.class);
    ParentFragmentInjectsChildFragment parentFragment = new ParentFragmentInjectsChildFragment();
    InjectableFragment childFragment = new InjectableFragment();

    activity.getFragmentManager().beginTransaction().add(parentFragment, "tag").commit();
    parentFragment
        .getChildFragmentManager()
        .beginTransaction()
        .add(childFragment, "child-tag")
        .commit();
    AndroidInjection.inject(childFragment);

    assertThat(childFragment.tag).isEqualTo("injected by parent fragment");
  }

  @Test
  public void injectActivity_applicationDoesntImplementHasDispatchingActivityInjector() {
    Activity activity = Robolectric.setupActivity(Activity.class);

    try {
      AndroidInjection.inject(activity);
      fail();
    } catch (Exception e) {
      assertThat(e)
          .hasMessageThat()
          .contains("Application does not implement dagger.android.HasDispatchingActivityInjector");
    }
  }

  @Test
  public void injectFragment_hasDispatchingFragmentInjectorNotFound() {
    Fragment fragment = new Fragment();
    FragmentTestUtil.startFragment(fragment);

    try {
      AndroidInjection.inject(fragment);
      fail();
    } catch (Exception e) {
      assertThat(e).hasMessageThat().contains("No injector was found");
    }
  }

  private static class ApplicationReturnsNull extends Application
      implements HasDispatchingActivityInjector, HasDispatchingFragmentInjector {
    @Override
    public DispatchingAndroidInjector<Activity> activityInjector() {
      return null;
    }

    @Override
    public DispatchingAndroidInjector<Fragment> fragmentInjector() {
      return null;
    }
  }

  @Test
  @Config(manifest = Config.NONE, application = ApplicationReturnsNull.class)
  public void dispatchingAndroidInjector_returnsNull() {
    Activity activity = Robolectric.setupActivity(Activity.class);

    try {
      AndroidInjection.inject(activity);
      fail();
    } catch (Exception e) {
      assertThat(e).hasMessageThat().contains("activityInjector() returned null");
    }
  }

  @Test
  @Config(manifest = Config.NONE, application = ApplicationReturnsNull.class)
  public void dispatchingFragmentInjector_returnsNull() {
    Fragment fragment = new Fragment();
    FragmentTestUtil.startFragment(fragment);

    try {
      AndroidInjection.inject(fragment);
      fail();
    } catch (Exception e) {
      assertThat(e).hasMessageThat().contains("fragmentInjector() returned null");
    }
  }

  @Test
  public void injectActivity_nullInput() {
    try {
      AndroidInjection.inject((Activity) null);
      fail();
    } catch (NullPointerException e) {
      assertThat(e).hasMessageThat().contains("activity");
    }
  }

  @Test
  public void injectFragment_nullInput() {
    try {
      AndroidInjection.inject((Fragment) null);
      fail();
    } catch (NullPointerException e) {
      assertThat(e).hasMessageThat().contains("fragment");
    }
  }
}