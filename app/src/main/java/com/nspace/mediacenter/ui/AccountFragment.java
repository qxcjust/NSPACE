package com.nspace.mediacenter.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.nspace.mediacenter.R;
import com.nspace.mediacenter.core.AccountManager;
import com.nspace.mediacenter.model.UserAccount;

/**
 * Account surface: sign-in (email / provider) and sign-out.
 *
 * <p>No credentials are persisted; see {@link AccountManager} for the privacy model.
 */
public final class AccountFragment extends Fragment {

  @Nullable
  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
      @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_account, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    TextView title = view.findViewById(R.id.list_title);
    title.setText(R.string.nav_account);
    render(view);
  }

  @Override
  public void onResume() {
    super.onResume();
    render(getView());
  }

  private void render(View view) {
    if (view == null) {
      return;
    }
    final AccountManager accounts = AccountManager.getInstance();
    ViewGroup signedOut = view.findViewById(R.id.account_signed_out);
    ViewGroup signedIn = view.findViewById(R.id.account_signed_in);
    TextView info = view.findViewById(R.id.account_info);

    if (accounts.isSignedIn()) {
      signedOut.setVisibility(View.GONE);
      signedIn.setVisibility(View.VISIBLE);
      UserAccount account = accounts.getCurrent();
      if (account != null) {
        info.setText(getString(R.string.account_signed_in_as,
            account.getDisplayName(), account.getProvider().name()));
      }
      Button signOut = view.findViewById(R.id.button_sign_out);
      signOut.setOnClickListener(v -> {
        accounts.signOut();
        render(view);
      });
      return;
    }

    signedOut.setVisibility(View.VISIBLE);
    signedIn.setVisibility(View.GONE);

    final EditText email = view.findViewById(R.id.edit_email);
    final EditText password = view.findViewById(R.id.edit_password);
    Button signIn = view.findViewById(R.id.button_sign_in);
    Button google = view.findViewById(R.id.button_google);
    Button qr = view.findViewById(R.id.button_qr);

    signIn.setOnClickListener(v -> {
      String mail = email.getText().toString().trim();
      String pass = password.getText().toString();
      if (mail.isEmpty() || pass.isEmpty()) {
        return;
      }
      accounts.signInWithEmail(mail, pass);
      password.setText("");
      render(view);
    });
    google.setOnClickListener(v -> {
      accounts.signInWithProvider(UserAccount.Provider.GOOGLE, "google-token-" + System.nanoTime());
      render(view);
    });
    qr.setOnClickListener(v -> {
      accounts.signInWithProvider(UserAccount.Provider.QR, "qr-token-" + System.nanoTime());
      render(view);
    });
  }
}
