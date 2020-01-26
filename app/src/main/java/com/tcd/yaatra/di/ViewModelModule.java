package com.tcd.yaatra.di;

import androidx.lifecycle.ViewModelProvider;

import com.tcd.yaatra.ui.viewmodels.LoginActivityViewModel;
import com.tcd.yaatra.ui.viewmodels.ViewModelFactory;

import dagger.Binds;
import dagger.Module;
import dagger.multibindings.IntoMap;

@Module
public abstract class ViewModelModule {

    @Binds
    abstract ViewModelProvider.Factory bindViewModelFactory(ViewModelFactory viewModelFactory);

    @Binds
    @IntoMap
    @ViewModelKey(LoginActivityViewModel.class)
    abstract LoginActivityViewModel bindLoginActivityViewModel(LoginActivityViewModel loginActivityViewModel);
}
