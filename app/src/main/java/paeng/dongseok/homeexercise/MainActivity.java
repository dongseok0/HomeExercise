package paeng.dongseok.homeexercise;

import android.databinding.DataBindingUtil;
import android.databinding.ObservableField;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;

import com.trello.rxlifecycle.ActivityEvent;
import com.trello.rxlifecycle.RxLifecycle;

import paeng.dongseok.homeexercise.databinding.ActivityMainBinding;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.subjects.BehaviorSubject;

public class MainActivity extends AppCompatActivity {

    private ObservableField<String> result = new ObservableField<>();
    private ActivityMainBinding binding;
    private Parser parser;
    private final BehaviorSubject<ActivityEvent> lifecycleSubject = BehaviorSubject.create();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main);
        binding.setResult(result);

        parser = new Parser();
    }

    public void onClickRun(final View v) {
        parser.parse(binding.input.getText().toString())
                .observeOn(AndroidSchedulers.mainThread())
                .compose(RxLifecycle.<String, ActivityEvent>bindUntilEvent(lifecycleSubject, ActivityEvent.PAUSE))
                .doOnSubscribe(new Action0() {
                    @Override
                    public void call() {
                        v.setEnabled(false);
                        result.set("Processing...");
                    }
                })
                .doOnTerminate(new Action0() {
                    @Override
                    public void call() {
                        v.setEnabled(true);
                    }
                })
                .subscribe(new Action1<String>() {
                    @Override
                    public void call(String s) {
                        result.set(s);
                    }
                }, new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        result.set(throwable.getMessage());
                    }
                });
    }

    @Override
    protected void onPause() {
        super.onPause();
        lifecycleSubject.onNext(ActivityEvent.PAUSE);
    }
}
