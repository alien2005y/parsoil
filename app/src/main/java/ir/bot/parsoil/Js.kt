package ir.bot.parsoil

object Js {
    private const val HELPERS = """
      var norm=function(s){return (s||'').replace(/[*:\s‌]/g,'')};
      var vis=function(e){return e&&e.offsetParent!==null};
      var byLabel=function(t){
        var els=document.querySelectorAll('label,span,div,td,th,p,b,strong');
        for(var i=0;i<els.length;i++){var e=els[i];
          if(e.children.length>2)continue;
          if(norm(e.textContent)!==norm(t))continue;
          var c=e.htmlFor&&document.getElementById(e.htmlFor);
          if(!c){var p=e;for(var k=0;k<4&&p&&!c;k++){p=p.parentElement;
            if(p)c=p.querySelector('input:not([type=hidden]):not([type=radio]):not([type=file]),select,textarea');}}
          if(c)return c;}
        return null};
    """
    // فرم باز است اگر فیلد «کد ملی» قابل مشاهده باشد
    const val IS_OPEN = "(function(){$HELPERS var e=byLabel('کد ملی');return !!(e&&vis(e));})()"

    // دکمه/کارت فعالِ نوبت را پیدا و کلیک می‌کند؛ متن را برمی‌گرداند ('' = چیزی نبود)
    // الف) متن دکمه کلیدواژه دارد  ب) خودِ کارت تاریخ‌دار فعال شده  ج) دکمه فعال داخل یک کارت تاریخ‌دار
    const val CLICK_BOOK = """(function(){
      var n=function(s){return (s||'').replace(/[\s\u200c\u200f]/g,'').replace(/ي/g,'ی').replace(/ك/g,'ک')};
      var keys=['نوبتگیری','دریافتنوبت','ثبتنوبت','رزرونوبت','دریافت','رزرو','ثبتنام'];
      var isDate=/[۰-۹0-9]{4}\/[۰-۹0-9]{2}\/[۰-۹0-9]{2}/;
      var bad=['تکمیل','بازگشت','خروج','پیگیری','منو'];
      var els=document.querySelectorAll('a,button,input[type=button],input[type=submit],[role=button]');
      var off=function(e){return e.disabled||e.getAttribute('aria-disabled')==='true'||/disabled/i.test(e.className)||e.offsetParent===null||getComputedStyle(e).pointerEvents==='none'};
      var inCard=function(e){var p=e.parentElement;for(var k=0;k<4&&p;k++,p=p.parentElement){if(isDate.test(p.innerText||''))return true}return false};
      for(var i=0;i<els.length;i++){var e=els[i];
        var raw=(e.innerText||e.value||e.textContent||'').trim(), t=n(raw);
        if(!t||e.closest('nav,header,.navbar,footer'))continue;
        var skip=false;for(var b=0;b<bad.length;b++){if(t.indexOf(bad[b])>=0){skip=true;break}}
        if(skip||off(e))continue;
        var hit=false;for(var k=0;k<keys.length;k++){if(t.indexOf(keys[k])>=0){hit=true;break}}
        if(!hit)hit=isDate.test(raw)||inCard(e);
        if(!hit)continue;
        e.click();return raw.replace(/\s+/g,' ').slice(0,40)}
      return ''})()"""

    // برای لاگ: دکمه‌ها/لینک‌های داخل صفحه (متن | غیرفعال؟)
    const val CANDIDATES = """(function(){var o=[];
      document.querySelectorAll('a,button,input[type=button],input[type=submit]').forEach(function(e){
        if(e.closest('nav,header,.navbar,footer'))return;
        var t=(e.innerText||e.value||'').trim().slice(0,28);if(!t)return;
        o.push(t+'|'+((e.disabled||/disabled/i.test(e.className))?'غیرفعال':'فعال'))});
      return 'title='+document.title+' ; '+o.slice(0,12).join(' ; ')+' ; متن صفحه='+((document.body&&document.body.innerText)||'').replace(/\s+/g,' ').trim().slice(0,160)})()"""

    // data = پروفایل؛ خروجی = JSON وضعیت هر فیلد (ok / missing / no-option / rejected ...)
    const val FILL = """function(d){
      $HELPERS
      var rep={};
      var ev=function(e,n){e.dispatchEvent(new Event(n,{bubbles:true}))};
      var setV=function(e,v,name){
        if(!e){rep[name]='missing';return}
        if(v==null||v===''){rep[name]='empty-profile';return}
        var proto=e.tagName==='SELECT'?HTMLSelectElement.prototype:HTMLInputElement.prototype;
        var s=Object.getOwnPropertyDescriptor(proto,'value').set;
        if(e.tagName==='SELECT'){var ok=false;
          for(var i=0;i<e.options.length;i++){if(e.options[i].text.indexOf(v)>=0){s.call(e,e.options[i].value);ok=true;break}}
          if(!ok){rep[name]='no-option';return}
          if(window.jQuery){try{jQuery(e).val(e.value).trigger('change')}catch(x){}}}
        else s.call(e,v);
        ev(e,'input');ev(e,'change');ev(e,'keyup');ev(e,'blur');
        rep[name]=(e.value&&e.value.length)?'ok':'rejected'};
      setV(byLabel('کد ملی'),d.nationalId,'nationalId');
      var mob=document.querySelector('input[placeholder*="9XXXXXXXXX"],input[placeholder*="09"]')||byLabel('موبایل');
      setV(mob,d.mobile,'mobile');
      setV(byLabel('نام'),d.first,'first');
      setV(byLabel('نام خانوادگی'),d.last,'last');
      setV(byLabel('تاریخ تولد'),d.birth,'birth');
      var rs=document.querySelectorAll('input[type=radio]');rep.gender='missing';
      for(var i=0;i<rs.length;i++){var lab=norm((rs[i].parentElement||{}).textContent);
        if(lab.indexOf(d.male?'مرد':'زن')>=0&&(d.male||lab.indexOf('مرد')<0)){rs[i].click();rep.gender=rs[i].checked?'ok':'rejected';break}}
      setV(byLabel('VIN'),d.vin,'vin');
      setV(byLabel('مدل خودرو'),d.model,'model');
      setV(byLabel('کیلومتر فعلی'),d.km,'km');
      setV(byLabel('نوع خودرو'),d.carType,'carType');
      // پلاک: بر اساس موقعیت روی صفحه، نه ترتیب DOM
      var lbl=byLabel('شماره پلاک');var box=lbl;
      for(var k=0;k<5&&box;k++){box=box.parentElement;
        if(box&&box.querySelectorAll('input:not([type=hidden]),select').length>=4)break}
      if(!box){rep.plate='box-missing'}else{
        var cs=[].slice.call(box.querySelectorAll('input:not([type=hidden]),select')).filter(vis);
        var sel=cs.filter(function(c){return c.tagName==='SELECT'})[0];
        var ins=cs.filter(function(c){return c.tagName!=='SELECT'});
        ins.sort(function(a,b){return a.getBoundingClientRect().left-b.getBoundingClientRect().left});
        rep.plateInputs=ins.length+(sel?'+select':'');
        if(ins.length>=3){setV(ins[0],d.plate2,'plate2');setV(ins[1],d.plate3,'plate3');setV(ins[2],d.plateCity,'plateCity')}
        else rep.plate='inputs-found-'+ins.length;
        setV(sel,d.plateLetter,'plateLetter');
      }
      // عکس‌ها: اولین input فایل = روی کارت، دومی = پشت کارت
      var fs=document.querySelectorAll('input[type=file]');rep.fileInputs=fs.length;
      var put=function(inp,data,name,key){
        if(!data){rep[key]='no-image-in-profile';return}
        if(!inp){rep[key]='no-file-input';return}
        try{var a=data.split(',')[1],bin=atob(a),u=new Uint8Array(bin.length);
          for(var i=0;i<bin.length;i++)u[i]=bin.charCodeAt(i);
          var f=new File([u],name,{type:'image/jpeg'}),dt=new DataTransfer();dt.items.add(f);
          inp.files=dt.files;ev(inp,'change');rep[key]=inp.files.length?'ok':'rejected'}
        catch(x){rep[key]='error:'+x.message}};
      put(fs[0],d.frontData,'front.jpg','imgFront');put(fs[1],d.backData,'back.jpg','imgBack');
      if(d.focusCap){var cap=byLabel('کد امنیتی');
        if(cap){cap.scrollIntoView({block:'center'});cap.focus();rep.captcha='focused'}else rep.captcha='missing'}
      return JSON.stringify(rep)}"""

    // لیست idها و برچسب‌ها برای عیب‌یابی
    const val INSPECT = """(function(){var o=[];
      document.querySelectorAll('input,select,textarea').forEach(function(e){
        o.push(e.tagName+' type='+e.type+' id='+e.id+' name='+e.name+' ph='+(e.placeholder||'')+' vis='+(e.offsetParent!==null))});
      document.querySelectorAll('a,button,input[type=button],input[type=submit]').forEach(function(e){
        o.push('CLICK '+e.tagName+' txt='+(e.innerText||e.value||'').trim().slice(0,30)+' cls='+e.className+' dis='+(e.disabled||/disabled/i.test(e.className))+' href='+(e.getAttribute('href')||''))});
      o.unshift('TITLE='+document.title+' URL='+location.href);
      return o.join('\n')})()"""
}
