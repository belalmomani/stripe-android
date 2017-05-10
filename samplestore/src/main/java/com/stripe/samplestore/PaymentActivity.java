package com.stripe.samplestore;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.wallet.Cart;
import com.google.android.gms.wallet.LineItem;
import com.jakewharton.rxbinding.view.RxView;
import com.stripe.android.Stripe;
import com.stripe.android.model.Card;
import com.stripe.android.model.Source;
import com.stripe.android.model.SourceCardData;
import com.stripe.android.model.SourceParams;
import com.stripe.android.view.CardInputWidget;
import com.stripe.wrap.pay.AndroidPayConfiguration;
import com.stripe.wrap.pay.activity.StripeAndroidPayActivity;
import com.stripe.wrap.pay.utils.CartManager;

import java.util.Currency;
import java.util.Locale;
import java.util.concurrent.Callable;

import retrofit2.Retrofit;
import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.schedulers.Schedulers;
import rx.subscriptions.CompositeSubscription;

public class PaymentActivity extends StripeAndroidPayActivity {

    private static final String EXTRA_CART = "EXTRA_CART";

    private CardInputWidget mCardInputWidget;
    private CompositeSubscription mCompositeSubscription;
    private ProgressDialogFragment mProgressDialogFragment;
    private ShoppingCartAdapter mShoppingCartAdapter;
    private Stripe mStripe;

    public static Intent createIntent(@NonNull Context context, @NonNull Cart cart) {
        Intent intent = new Intent(context, PaymentActivity.class);
        intent.putExtra(EXTRA_CART, cart);
        return intent;
    }

    @Override
    protected void onAndroidPayAvailable() {

    }

    @Override
    protected void onAndroidPayNotAvailable() {

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_payment);

        Bundle extras = getIntent().getExtras();
        Cart cart = extras.getParcelable(EXTRA_CART);
        ItemDivider dividerDecoration = new ItemDivider(this, R.drawable.item_divider);
        RecyclerView.LayoutManager linearLayoutManager = new LinearLayoutManager(this);
        RecyclerView recyclerView = (RecyclerView) findViewById(R.id.rv_payment);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(linearLayoutManager);
        recyclerView.addItemDecoration(dividerDecoration);
        mShoppingCartAdapter = new ShoppingCartAdapter(cart);
        recyclerView.setAdapter(mShoppingCartAdapter);


//        TextView priceDisplay = (TextView) findViewById(R.id.tv_price_display);
//        priceDisplay.setText(StoreUtils.getPriceString(mPrice, mCurrency));
        mCompositeSubscription = new CompositeSubscription();

        mCardInputWidget = (CardInputWidget) findViewById(R.id.card_input_widget);
        mProgressDialogFragment =
                ProgressDialogFragment.newInstance(R.string.completing_purchase);
        Button payButton = (Button) findViewById(R.id.btn_purchase);
//        payButton.setText(String.format(Locale.ENGLISH,
//                "Pay %s", StoreUtils.getPriceString(mPrice, null)));
//        RxView.clicks(payButton)
//                .subscribe(new Action1<Void>() {
//                    @Override
//                    public void call(Void aVoid) {
//                        attemptPurchase();
//                    }
//                });

        mStripe = new Stripe(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mCompositeSubscription != null) {
            mCompositeSubscription.unsubscribe();
            mCompositeSubscription = null;
        }
    }

    private void attemptPurchase() {
        Card card = mCardInputWidget.getCard();
        if (card == null) {
            displayError("Card Input Error");
            return;
        }
        dismissKeyboard();

        final SourceParams cardParams = SourceParams.createCardParams(card);
        Observable<Source> cardSourceObservable =
                Observable.fromCallable(new Callable<Source>() {
                    @Override
                    public Source call() throws Exception {
                        return mStripe.createSourceSynchronous(
                                cardParams,
                                AndroidPayConfiguration.getInstance().getPublicApiKey());
                    }
                });

        final FragmentManager fragmentManager = this.getSupportFragmentManager();
        mCompositeSubscription.add(cardSourceObservable
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe(
                    new Action0() {
                        @Override
                        public void call() {
                            mProgressDialogFragment.show(fragmentManager, "progress");
                        }
                    })
                .subscribe(
                    new Action1<Source>() {
                        @Override
                        public void call(Source source) {
                            proceedWithPurchaseIf3DSCheckIsNotNecessary(source);
                        }
                    },
                    new Action1<Throwable>() {
                        @Override
                        public void call(Throwable throwable) {
                            if (mProgressDialogFragment != null) {
                                mProgressDialogFragment.dismiss();
                            }
                            displayError(throwable.getLocalizedMessage());
                        }
                    }));
    }

    private void proceedWithPurchaseIf3DSCheckIsNotNecessary(Source source) {
        if (source == null || !Source.CARD.equals(source.getType())) {
            displayError("Something went wrong - this should be rare");
            return;
        }

        SourceCardData cardData = (SourceCardData) source.getSourceTypeModel();
        if (SourceCardData.REQUIRED.equals(cardData.getThreeDSecureStatus())) {
            // In this case, you would need to ask the user to verify the purchase.
            // You can see an example of how to do this in the 3DS example application.
            // In stripe-android/example.
        } else {
            // If 3DS is not required, you can charge the source.
            completePurchase(source);
        }
    }

    private void completePurchase(Source source) {
        Retrofit retrofit = RetrofitFactory.getInstance();
        StripeService stripeService = retrofit.create(StripeService.class);
//        Observable<Void> stripeResponse = stripeService.createQueryCharge(mPrice, source.getId());

//        mCompositeSubscription.add(stripeResponse
//                .subscribeOn(Schedulers.io())
//                .observeOn(AndroidSchedulers.mainThread())
//                .doOnUnsubscribe(
//                        new Action0() {
//                            @Override
//                            public void call() {
//                                if (mProgressDialogFragment != null) {
//                                    mProgressDialogFragment.dismiss();
//                                }
//                            }
//                        })
//                .subscribe(
//                    new Action1<Void>() {
//                        @Override
//                        public void call(Void aVoid) {
//                            finishCharge();
//                        }
//                    },
//                    new Action1<Throwable>() {
//                        @Override
//                        public void call(Throwable throwable) {
//                            displayError(throwable.getLocalizedMessage());
//                        }
//                    }));
    }

    private void displayError(String errorMessage) {
        AlertDialog alertDialog = new AlertDialog.Builder(this).create();
        alertDialog.setTitle("Error");
        alertDialog.setMessage(errorMessage);
        alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
        alertDialog.show();
    }

    private void finishCharge() {
//        Intent data = StoreActivity.createPurchaseCompleteIntent(mEmojiUnicode, mPrice);
//        setResult(RESULT_OK, data);
        finish();
    }

    private void dismissKeyboard() {
        InputMethodManager inputManager =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        inputManager.toggleSoftInput(0, 0);
//        mEmojiView.requestFocus();
    }

}
