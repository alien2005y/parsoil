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
    // فرم باز است اگر فیلد NationalId داخل attForm دیده شود
    const val IS_OPEN = "(function(){var e=document.getElementById('NationalId');return !!(e&&e.offsetParent!==null&&document.getElementById('attForm'));})()"

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

    // data = پروفایل؛ خروجی = JSON وضعیت هر فیلد. idها از HTML واقعی صفحه ثبت‌نام گرفته شده.
    // فیلد honeypot به اسم Website عمداً دست نمی‌خورد (باید خالی بماند).
    const val FILL = """function(d){
      var rep={};
      var g=function(id){return document.getElementById(id)};
      var ev=function(e,n){e.dispatchEvent(new Event(n,{bubbles:true}))};
      var nz=function(s){return (s||'').replace(/ي/g,'ی').replace(/ك/g,'ک').replace(/[\s\u200c]+/g,' ').trim()};
      var setText=function(id,v,key){
        var e=g(id);
        if(!e){rep[key]='missing';return}
        if(v==null||v===''){rep[key]='empty-profile';return}
        Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(e,v);
        ev(e,'input');ev(e,'change');ev(e,'keyup');ev(e,'blur');
        rep[key]=(e.value===v)?'ok':'rejected'};
      var setSel=function(id,v,key){
        var e=g(id);
        if(!e){rep[key]='missing';return}
        if(v==null||v===''){rep[key]='empty-profile';return}
        var want=nz(v),hit=null,i,o;
        for(i=0;i<e.options.length;i++){o=e.options[i];if(o.value===v||nz(o.text)===want){hit=o;break}}
        if(!hit){for(i=0;i<e.options.length;i++){o=e.options[i];if(o.value&&nz(o.text).indexOf(want)>=0){hit=o;break}}}
        if(!hit){rep[key]='no-option';return}
        Object.getOwnPropertyDescriptor(HTMLSelectElement.prototype,'value').set.call(e,hit.value);
        if(window.jQuery){try{jQuery(e).val(hit.value).trigger('change')}catch(x){}}else{ev(e,'change')}
        rep[key]=(e.value===hit.value)?'ok':'rejected'};
      setText('NationalId',d.nationalId,'nationalId');
      setText('Mobile',d.mobile,'mobile');
      setText('F_Name',d.first,'first');
      setText('L_Name',d.last,'last');
      var gr=g(d.male?'Gender_0':'Gender_1');
      if(gr){gr.click();rep.gender=gr.checked?'ok':'rejected'}else{rep.gender='missing'}
      setText('VIN',d.vin,'vin');
      setSel('CarModel',d.model,'model');
      setText('Kilometer',d.km,'km');
      setSel('Type_Car',d.carType,'carType');
      setText('twoDigit',d.plate2,'plate2');
      setSel('letter',d.plateLetter,'plateLetter');
      setText('threeDigit',d.plate3,'plate3');
      setText('irDigit',d.plateCity,'plateCity');
      // تاریخ تولد: در نسخه فعلی فرم وجود ندارد؛ اگر برگشت پر می‌شود
      var bd=g('BirthDateString');
      if(bd){setText('BirthDateString',d.birth,'birth')}else{rep.birth='not-on-page'}
      // عکس‌ها: سایت خودش روی رویداد change فشرده‌سازی و اعتبارسنجی می‌کند
      var put=function(id,data,name,key){
        var inp=g(id);
        if(!data){rep[key]='no-image-in-profile';return}
        if(!inp){rep[key]='no-file-input';return}
        if(inp.files&&inp.files.length&&inp._botDone){rep[key]='ok';return}
        try{var a=data.split(',')[1],bin=atob(a),u=new Uint8Array(bin.length);
          for(var i=0;i<bin.length;i++)u[i]=bin.charCodeAt(i);
          var dt=new DataTransfer();
          dt.items.add(new File([u],name,{type:'image/jpeg',lastModified:Date.now()}));
          inp.files=dt.files;inp._botDone=true;ev(inp,'change');
          rep[key]=(inp.files&&inp.files.length)?'ok':'rejected'}
        catch(x){rep[key]='error:'+x.message}};
      put('carCardFront',d.frontData,'front.jpg','imgFront');
      put('carCardBack',d.backData,'back.jpg','imgBack');
      if(d.focusCap){var cap=g('Captcha');
        if(cap){cap.scrollIntoView({block:'center'});cap.focus();rep.captcha='focused'}else{rep.captcha='missing'}}
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
