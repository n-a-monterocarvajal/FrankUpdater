package io.github.n_a_monterocarvajal.frankupdater.installer;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
interface IInstallerService {
    Bundle execute(in String[] arguments, in ParcelFileDescriptor input) = 1;
    void destroy() = 2;
}
